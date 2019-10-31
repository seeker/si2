package com.github.seeker.app;

import static org.awaitility.Awaitility.to;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.messaging.HashMessageHelper;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.Hash;
import com.github.seeker.persistence.document.ImageMetaData;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import de.caluga.morphium.Morphium;

public class FileProcessorIT {

	private static final String ANCHOR = "testimages";
	
	private static final String IMAGE_AUTUMN = "autumn.jpg";
	private static final String IMAGE_ROAD_FAR = "road-far.jpg";
	
	private static final byte[] AUTUMN_SHA256 = {48, -34, -2, 126, 61, -52, 0, -100, -51, 53, 101, -79, 68, -60, -85, -90, 24, 84, -14, -12, -20, -125, -38, -27, 46, -53, -115, 33, -66, 68, 6, 91};
	
	private static ConnectionProvider connectionProvider;

	private FileProcessor cut;
	private MongoDbMapper mapperForTest; 
	private Connection rabbitConn;
	private Channel channelForTest;
	private QueueConfiguration queueConfig;
	
	private LinkedBlockingQueue<Map<String, Hash>> hashMessages;
	private LinkedBlockingQueue<Map<String, Object>> messageHeaders;
	private LinkedBlockingQueue<Byte[]> thumbMessage;
	
    @Rule
    public Timeout globalTimeout = new Timeout((int)TimeUnit.MILLISECONDS.convert(20, TimeUnit.SECONDS));
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ConsulConfiguration consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		connectionProvider = new ConnectionProvider(consulConfig);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		rabbitConn = connectionProvider.getRabbitMQConnection();
		ConsulClient consul = connectionProvider.getConsulClient();
		
		Channel channel = rabbitConn.createChannel();
		channelForTest = rabbitConn.createChannel();
		
		mapperForTest = connectionProvider.getIntegrationMongoDbMapper();
		
		queueConfig = new QueueConfiguration(channel, consul, true);
		
		cut = new FileProcessor(channel, consul, queueConfig);
		
		hashMessages = new LinkedBlockingQueue<Map<String, Hash>>();
		thumbMessage = new LinkedBlockingQueue<Byte[]>();
		messageHeaders = new LinkedBlockingQueue<Map<String,Object>>();
		
		MessageDigest md =  MessageDigest.getInstance("SHA-256");
		
		channel.basicConsume(queueConfig.getQueueName(ConfiguredQueues.hashes), new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
				
				HashMessageHelper hmh = new HashMessageHelper();
				
				try {
					hashMessages.add(hmh.decodeHashMessage(properties.getHeaders(), body));
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}

				messageHeaders.add(properties.getHeaders());
			}
		});
		
		channel.basicConsume(queueConfig.getQueueName(ConfiguredQueues.thumbnails), new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
				md.reset(); 
		
				byte[] digest = md.digest();
				Byte[] boxedDigest = new Byte[digest.length];
				
				for (int i = 0; i < digest.length; i++) {
					boxedDigest[i] = digest[i];
				}
				
				thumbMessage.add(boxedDigest);
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
		headers.put("thumb", Boolean.toString(hasThumbnail));
		headers.put(MessageHeaderKeys.HASH_ALGORITHMS, "SHA-256");
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();
		
		channelForTest.basicPublish(QueueConfiguration.FILE_LOADER_EXCHANGE, "", props, rawImageData);
	}

	@After
	public void tearDown() throws Exception {
		rabbitConn.close();
		
		Morphium dbClient = connectionProvider.getMorphiumClient(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);
		dbClient.clearCachefor(ImageMetaData.class);
		dbClient.dropCollection(ImageMetaData.class);
	}
	
	@Test
	public void hashResponseIsReceived() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), true);
		
		Awaitility.await().atMost(10, TimeUnit.SECONDS).untilCall(to(hashMessages).size(), is(1));
	}
	
	@Test
	public void sha256IsCorrect() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), true);
		Map<String, Hash> message = hashMessages.take();
		
		assertThat(message.get("SHA-256").getHash(), is(AUTUMN_SHA256));
	}
}
