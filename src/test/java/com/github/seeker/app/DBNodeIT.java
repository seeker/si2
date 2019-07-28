package com.github.seeker.app;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import static org.awaitility.Awaitility.to;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import de.caluga.morphium.Morphium;

public class DBNodeIT {
	private static final String ANCHOR = "anchorman";
	private static final Path RELATIVE_ANCHOR_PATH = Paths.get("foo/bar/baz/boo.jpg");
	private static final byte[] SHA256 = {34,53,23,1,4,3,6,4};
	private static final long PHASH = 348759L;
	
	private static ConnectionProvider connectionProvider;

	private DBNode cut;
	private Channel channelForTests;
	private MongoDbMapper mapperForTest; 
	private Connection rabbitConn;
	
	private Map<String, Object> headers;
	
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
		channelForTests = rabbitConn.createChannel();
		mapperForTest = connectionProvider.getIntegrationMongoDbMapper();
		
		QueueConfiguration queueConfig = new QueueConfiguration(channel, consul);
		
		cut = new DBNode(consul, connectionProvider.getIntegrationMongoDbMapper(), rabbitConn, queueConfig);
		headers = createTestHeaders();
		
		AMQP.BasicProperties hashProps = new AMQP.BasicProperties.Builder().headers(headers).build();
		channelForTests.basicPublish("", queueConfig.getQueueName(ConfiguredQueues.hashes), hashProps, null);
	}
	
	private Map<String, Object> createTestHeaders() {
		Map<String, Object> newHeaders = new HashMap<String, Object>();
		
		newHeaders.put("anchor", ANCHOR);
		newHeaders.put("path", RELATIVE_ANCHOR_PATH.toString());
		newHeaders.put("sha256", SHA256.toString());
		newHeaders.put("phash", Long.toString(PHASH));
		
		return newHeaders;
	}

	@After
	public void tearDown() throws Exception {
		rabbitConn.close();
		
		Morphium dbClient = connectionProvider.getMorphiumClient(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);
		dbClient.clearCachefor(ImageMetaData.class);
		dbClient.dropCollection(ImageMetaData.class);
	}
	
	@Test
	public void longFromHeaderIsReversible() {
		long parsedLong = Long.parseLong(headers.get("phash").toString());
		
		assertThat(parsedLong, is(PHASH));
	}
	
	@Ignore
	@Test
	public void binaryFromHeaderIsReversible() {
		//TODO need to think of a different solution - string encoding will be expensive... send as body?
		
		byte[] parsedByteArray = headers.get("phash").toString().getBytes();
		
		assertThat(parsedByteArray, is(SHA256));
	}
	
	@Test
	public void messageIsAddedToDatabase() throws Exception {
		Awaitility.await().atMost(5, TimeUnit.MINUTES).untilCall(to(mapperForTest).getImageMetadata(ANCHOR, RELATIVE_ANCHOR_PATH), is(notNullValue()));
	}
}
