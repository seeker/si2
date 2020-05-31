package com.github.seeker.app;

import static org.awaitility.Awaitility.to;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.VaultCredentials;
import com.github.seeker.configuration.VaultIntegrationCredentials;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredExchanges;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.HashMessageHelper;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.Hash;
import com.github.seeker.persistence.document.ImageMetaData;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import de.caluga.morphium.Morphium;

public class ImageResizerIT {
	private static final int AWAIT_TIMEOUT_SECONDS = 5;
	
	private static final String ANCHOR = "testimages";
	
	private static final String IMAGE_AUTUMN = "autumn.jpg";
	private static final String IMAGE_ROAD_FAR = "road-far.jpg";
	
	private static final byte[] AUTUMN_SHA256 = {48, -34, -2, 126, 61, -52, 0, -100, -51, 53, 101, -79, 68, -60, -85, -90, 24, 84, -14, -12, -20, -125, -38, -27, 46, -53, -115, 33, -66, 68, 6, 91};
	private static final byte[] AUTUMN_SHA512 = {32, -119, 47, -64, -97, -72, -41, -5, 106, 112, -38, -113, -115, -107, 25, 59, -38, -1, 22, -71, -63, 88, 119, -54, 91, 25, 124, 3, 17, 60, -57, 79, -87, -127, 89, -91, 27, -52, 10, -4, 102, -99, -69, -19, 42, -13, 17, -51, -93, -60, -128, -96, 88, -126, -21, 38, 14, 71, 105, 63, 100, 4, 92, -72};
	
	private static final String ALGORITHM_NAME_SHA256 = "SHA-256";
	private static final String ALGORITHM_NAME_SHA512 = "SHA-512";
	
	private static ConnectionProvider connectionProvider;

	private ImageResizer cut;
	private MongoDbMapper mapperForTest; 
	private Connection rabbitConn;
	private Channel channelForTest;
	private QueueConfiguration queueConfig;
	private ConsulClient consul;
	
	private LinkedBlockingQueue<Map<String, Object>> thumbMessageHeaders;
	private LinkedBlockingQueue<Map<String, Object>> preprocessedMessageHeaders;
	private LinkedBlockingQueue<Byte[]> thumbMessage;
	private LinkedBlockingQueue<Byte[]> preprocessedMessage;
	
    @Rule
    public Timeout globalTimeout = new Timeout((int)TimeUnit.MILLISECONDS.convert(20, TimeUnit.SECONDS));
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ConsulConfiguration consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		connectionProvider = new ConnectionProvider(consulConfig, new VaultIntegrationCredentials(Approle.integration), consulConfig.overrideVirtualBoxAddress());
		
		assertThat(connectionProvider, is(notNullValue()));
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		ConnectionFactory connFactory = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.integration);
		assertThat(connFactory, is(notNullValue()));
		
		rabbitConn = connFactory.newConnection();
		consul = connectionProvider.getConsulClient();
		
		Channel channel = rabbitConn.createChannel();
		channelForTest = rabbitConn.createChannel();
		
		mapperForTest = connectionProvider.getIntegrationMongoDbMapper();
		
		queueConfig = new QueueConfiguration(channel, true);
		
		cut = new ImageResizer(rabbitConn, consul, queueConfig);
		
		thumbMessage = new LinkedBlockingQueue<Byte[]>();
		preprocessedMessage = new LinkedBlockingQueue<Byte[]>();
		thumbMessageHeaders = new LinkedBlockingQueue<Map<String,Object>>();
		preprocessedMessageHeaders = new LinkedBlockingQueue<Map<String,Object>>();
		
		MessageDigest md =  MessageDigest.getInstance("SHA-256");
		
		channel.basicConsume(queueConfig.getQueueName(ConfiguredQueues.thumbnails), new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
				
					thumbMessageHeaders.add(properties.getHeaders());
					thumbMessage.add(ArrayUtils.toObject(body));
			}
		});
		
		channel.basicConsume(queueConfig.getQueueName(ConfiguredQueues.filePreProcessed), new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
		
				preprocessedMessageHeaders.add(properties.getHeaders());
				preprocessedMessage.add(ArrayUtils.toObject(body));
			}
		});
		
	}
	
	
	private Path getClassPathFile(String fileName) throws URISyntaxException {
		return Paths.get(ClassLoader.getSystemResource("images/"+fileName).toURI());
	}
	
	private void sendFileProcessMessage(Path image, boolean hasThumbnail) throws IOException {
		byte[] rawImageData = Files.readAllBytes(image);
		
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(MessageHeaderKeys.ANCHOR, ANCHOR);
		headers.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, image.toString());
		headers.put(MessageHeaderKeys.THUMBNAIL_FOUND, Boolean.toString(hasThumbnail));
		headers.put(MessageHeaderKeys.HASH_ALGORITHMS, "SHA-256,SHA-512");
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();
		
		channelForTest.basicPublish(queueConfig.getExchangeName(ConfiguredExchanges.loader), "", props, rawImageData);
	}

	@After
	public void tearDown() throws Exception {
		Channel channel = rabbitConn.createChannel();
		
		for (ConfiguredQueues queue : ConfiguredQueues.values()) {
			channel.queueDelete(queue.toString());
		}
		
		if (Objects.nonNull(rabbitConn)) {
			rabbitConn.close();
		}
		
		Morphium dbClient = connectionProvider.getMorphiumClient(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);
		dbClient.clearCachefor(ImageMetaData.class);
		dbClient.dropCollection(ImageMetaData.class);
	}
	
	@Test
	public void thumbnailGeneratedWhenMissing() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), false);
		
		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).untilCall(to(thumbMessage).size(), is(1));
	}
	
	@Test
	public void thumbnailNotGeneratedWhenPresent() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), true);
		
		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).untilCall(to(preprocessedMessage).size(), is(1));
		Awaitility.await().pollDelay(1, TimeUnit.SECONDS).atMost(AWAIT_TIMEOUT_SECONDS + 1, TimeUnit.SECONDS).untilCall(to(thumbMessage).size(), is(0));
	}
	
	@Test
	public void PreprocessResponseIsReceived() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), true);
		
		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).untilCall(to(preprocessedMessage).size(), is(1));
	}
	
	@Test

	public void thumbnailMessageContainsThumbnailSize() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), false);
		
		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).untilCall(to(thumbMessageHeaders).size(), is(1));
		assertThat(thumbMessageHeaders.take().get(MessageHeaderKeys.THUMBNAIL_SIZE), is(300));
	}
	
	public void thumbnailMessageHasSizeHeader() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), false);

		assertThat(thumbMessageHeaders.take().containsKey(MessageHeaderKeys.THUMBNAIL_SIZE), is(true));
	}
}
