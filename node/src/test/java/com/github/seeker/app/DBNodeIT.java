package com.github.seeker.app;

import static org.awaitility.Awaitility.to;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
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
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.configuration.VaultIntegrationCredentials;
import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;
import com.github.seeker.messaging.proto.DbUpdateOuterClass.DbUpdate;
import com.github.seeker.messaging.proto.DbUpdateOuterClass.UpdateType;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.Hash;
import com.github.seeker.persistence.document.ImageMetaData;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import de.caluga.morphium.Morphium;

public class DBNodeIT {
	private static final String ANCHOR = "anchorman";
	private static final Path RELATIVE_ANCHOR_PATH = Paths.get("foo/bar/baz/boo.jpg");
	private static final Path RELATIVE_ANCHOR_PATH_WITH_UMLAUT = Paths.get("foo/bar/bäz/böö.jpg");
	private static final byte[] SHA256 = {-29, -80, -60, 66, -104, -4, 28, 20, -102, -5, -12, -56, -103, 111, -71, 36, 39, -82, 65, -28, 100, -101, -109, 76, -92, -107, -103, 27, 120, 82, -72, 85};
	private static final String SHA256_ALGORITHM_NAME = "SHA-256";
	
	private static ConnectionProvider connectionProvider;

	@SuppressWarnings("unused")
	private DBNode cut;
	private MongoDbMapper mapperForTest; 
	private Connection rabbitConn;
	private DbUpdate prototype;
	private Duration duration;
	private QueueConfiguration queueConfig;
	private Channel channel;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ConsulConfiguration consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		connectionProvider = new ConnectionProvider(consulConfig, new VaultIntegrationCredentials(Approle.integration), consulConfig.overrideVirtualBoxAddress());
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		duration = new Duration(20, TimeUnit.SECONDS);
		
		rabbitConn = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.integration).newConnection();
		ConsulClient consul = connectionProvider.getConsulClient();
		
		channel = rabbitConn.createChannel();
		mapperForTest = connectionProvider.getIntegrationMongoDbMapper();
		
		queueConfig = new QueueConfiguration(channel, true);
		
		cut = new DBNode(consul, connectionProvider.getIntegrationMongoDbMapper(), rabbitConn, queueConfig);

		DbUpdate.Builder builder = DbUpdate.newBuilder().putDigestHash(SHA256_ALGORITHM_NAME, ByteString.copyFrom(SHA256)).setUpdateType(UpdateType.UPDATE_TYPE_DIGEST_HASH);
		builder.getImagePathBuilder().setAnchor(ANCHOR).setRelativePath(RELATIVE_ANCHOR_PATH.toString());

		this.prototype = builder.buildPartial();
	}
	
	private void sendMessage(DbUpdate message) throws IOException {
		channel.basicPublish("", queueConfig.getQueueName(ConfiguredQueues.persistence), null, message.toByteArray());
	}

	@After
	public void tearDown() throws Exception {
		queueConfig.deleteAllQueues();
		rabbitConn.close();
		
		Morphium dbClient = connectionProvider.getMorphiumClient(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);
		dbClient.clearCachefor(ImageMetaData.class);
		dbClient.dropCollection(ImageMetaData.class);
	}
	
	@Test
	public void messageIsAddedToDatabase() throws Exception {
		sendMessage(prototype);

		Awaitility.await().atMost(duration).untilCall(to(mapperForTest).getImageMetadata(ANCHOR, RELATIVE_ANCHOR_PATH), is(notNullValue()));
	}
	
	@Test
	public void pathWithUmlatusIsCorrectlyReceived() throws Exception {
		DbUpdate.Builder builder = DbUpdate.newBuilder(prototype);
		builder.getImagePathBuilder().setRelativePath(RELATIVE_ANCHOR_PATH_WITH_UMLAUT.toString());

		sendMessage(builder.build());

		Awaitility.await().atMost(duration).untilCall(to(mapperForTest).getImageMetadata(ANCHOR, RELATIVE_ANCHOR_PATH_WITH_UMLAUT), is(notNullValue()));
	}

	@Test
	public void digestHashIsUpdated() throws Exception {
		sendMessage(prototype);

		Awaitility.await().atMost(duration).untilCall(to(mapperForTest).getImageMetadata(ANCHOR, RELATIVE_ANCHOR_PATH), is(notNullValue()));

		Hash sha256 = mapperForTest.getImageMetadata(ANCHOR, RELATIVE_ANCHOR_PATH).getHashes().get(SHA256_ALGORITHM_NAME);

		assertArrayEquals(sha256.getHash(), SHA256);
	}

	@Test
	public void customHashIsUpdated() throws Exception {
		ByteArrayDataOutput phashAsByteArray = ByteStreams.newDataOutput();
		phashAsByteArray.writeLong(987439583L);

		DbUpdate.Builder builder = DbUpdate.newBuilder(prototype);
		builder.clearDigestHash();
		builder.putDigestHash("phash", ByteString.copyFrom(phashAsByteArray.toByteArray()));
		sendMessage(builder.build());

		Awaitility.await().atMost(duration).untilCall(to(mapperForTest).getImageMetadata(ANCHOR, RELATIVE_ANCHOR_PATH), is(notNullValue()));

		Hash phash = mapperForTest.getImageMetadata(ANCHOR, RELATIVE_ANCHOR_PATH).getHashes().get("phash");
		assertArrayEquals(phash.getHash(), phashAsByteArray.toByteArray());
	}
}
