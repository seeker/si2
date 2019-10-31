package com.github.seeker.app;

import static org.hamcrest.CoreMatchers.*;

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
import org.junit.Test;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.messaging.HashMessageBuilder;
import com.github.seeker.messaging.HashMessageHelper;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import de.caluga.morphium.Morphium;

public class DBNodeIT {
	private static final String ANCHOR = "anchorman";
	private static final Path RELATIVE_ANCHOR_PATH = Paths.get("foo/bar/baz/boo.jpg");
	private static final Path RELATIVE_ANCHOR_PATH_WITH_UMLAUT = Paths.get("foo/bar/bäz/böö.jpg");
	private static final byte[] SHA256 = {-29, -80, -60, 66, -104, -4, 28, 20, -102, -5, -12, -56, -103, 111, -71, 36, 39, -82, 65, -28, 100, -101, -109, 76, -92, -107, -103, 27, 120, 82, -72, 85};
	private static final long PHASH = 348759L;
	
	private static ConnectionProvider connectionProvider;

	private DBNode cut;
	private MongoDbMapper mapperForTest; 
	private Connection rabbitConn;
	private HashMessageBuilder hashMessage;
	
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
		mapperForTest = connectionProvider.getIntegrationMongoDbMapper();
		
		QueueConfiguration queueConfig = new QueueConfiguration(channel, consul, true);
		
		cut = new DBNode(consul, connectionProvider.getIntegrationMongoDbMapper(), rabbitConn, queueConfig);

		hashMessage = new HashMessageBuilder(channel, queueConfig);
		hashMessage.addHash("SHA-256", SHA256).send(createTestHeaders(RELATIVE_ANCHOR_PATH));
	}
	
	private Map<String, Object> createTestHeaders(Path releativeAnchorPath) {
		Map<String, Object> newHeaders = new HashMap<String, Object>();
		
		newHeaders.put("anchor", ANCHOR);
		newHeaders.put("path", releativeAnchorPath.toString());
		
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
	public void messageIsAddedToDatabase() throws Exception {
		Awaitility.await().atMost(5, TimeUnit.MINUTES).untilCall(to(mapperForTest).getImageMetadata(ANCHOR, RELATIVE_ANCHOR_PATH), is(notNullValue()));
	}
	
	@Test
	public void pathWithUmlatusIsCorrectlyReceived() throws Exception {
		hashMessage.addHash("SHA-256", SHA256).send(createTestHeaders(RELATIVE_ANCHOR_PATH_WITH_UMLAUT));
		
		Awaitility.await().atMost(5, TimeUnit.MINUTES).untilCall(to(mapperForTest).getImageMetadata(ANCHOR, RELATIVE_ANCHOR_PATH_WITH_UMLAUT), is(notNullValue()));
	}
}
