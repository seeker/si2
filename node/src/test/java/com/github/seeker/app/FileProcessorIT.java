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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.DatatypeConverter;

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
import com.github.seeker.messaging.HashMessage;
import com.github.seeker.messaging.HashMessageHelper;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.persistence.MongoDbMapper;
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
	private static final long AUTUMN_PHASH = -4012083468873271947L;
	private static final String IMAGE_AUTUMN_THUMB_HASH = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
	
	private static ConnectionProvider connectionProvider;

	private FileProcessor cut;
	private MongoDbMapper mapperForTest; 
	private Connection rabbitConn;
	private HashMessageHelper hashMessage;
	private Channel channelForTest;
	private QueueConfiguration queueConfig;
	
	private LinkedBlockingQueue<HashMessage> hashMessages;
	private LinkedBlockingQueue<String> thumbMessages;
	
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
		
		hashMessages = new LinkedBlockingQueue<HashMessage>();
		thumbMessages = new LinkedBlockingQueue<String>();
		
		MessageDigest md =  MessageDigest.getInstance("SHA-256");
		
		channel.basicConsume(queueConfig.getQueueName(ConfiguredQueues.hashes), new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
				
				HashMessageHelper hmh = new HashMessageHelper(channel, queueConfig);
				hashMessages.add(hmh.decodeHashMessage(properties.getHeaders(), body));
			}
		});
		
		channel.basicConsume(queueConfig.getQueueName(ConfiguredQueues.thumbnails), new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
				md.reset();
				md.digest(body);
				
				thumbMessages.add(DatatypeConverter.printHexBinary(md.digest()));
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
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();
		
		channelForTest.basicPublish("", queueConfig.getQueueName(ConfiguredQueues.files), props, rawImageData);
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
	public void phashIsCorrect() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), true);
		HashMessage message = hashMessages.take();
		
		assertThat(message.getPhash(), is(AUTUMN_PHASH));
	}
	
	@Test
	public void sha256IsCorrect() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), true);
		HashMessage message = hashMessages.take();
		
		assertThat(message.getSha256(), is(AUTUMN_SHA256));
	}
	
	@Test
	public void thumbnailIsCorrect() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), false);
		String imageDataHash = thumbMessages.take();
		
		assertThat(imageDataHash, is(IMAGE_AUTUMN_THUMB_HASH));
	}
}
