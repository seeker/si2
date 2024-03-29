package com.github.seeker.app;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.MinioConfiguration;
import com.github.seeker.configuration.MinioConfiguration.BucketKey;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredExchanges;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.configuration.VaultIntegrationCredentials;
import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;
import com.github.seeker.helpers.MinioTestHelper;
import com.github.seeker.messaging.proto.DbUpdateOuterClass.DbUpdate;
import com.github.seeker.messaging.proto.FileLoadOuterClass.FileLoad;
import com.github.seeker.messaging.proto.FileLoadOuterClass.FileLoad.Builder;
import com.github.seeker.persistence.MinioStore;
import com.github.seeker.persistence.document.ImageMetaData;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import de.caluga.morphium.Morphium;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;

@Timeout(value = 20)
public class MessageDigestHasherIT {

	private static final String ANCHOR = "testimages";
	private static final String TEST_BUCKET_NAME = MinioConfiguration.integrationTestBuckets().get(BucketKey.Si2);
	
	private static final String IMAGE_AUTUMN = "autumn.jpg";
	private static final UUID IMAGE_AUTUMN_UUID = UUID.randomUUID();
	
	private static final byte[] AUTUMN_SHA256 = {48, -34, -2, 126, 61, -52, 0, -100, -51, 53, 101, -79, 68, -60, -85, -90, 24, 84, -14, -12, -20, -125, -38, -27, 46, -53, -115, 33, -66, 68, 6, 91};
	private static final byte[] AUTUMN_SHA512 = {32, -119, 47, -64, -97, -72, -41, -5, 106, 112, -38, -113, -115, -107, 25, 59, -38, -1, 22, -71, -63, 88, 119, -54, 91, 25, 124, 3, 17, 60, -57, 79, -87, -127, 89, -91, 27, -52, 10, -4, 102, -99, -69, -19, 42, -13, 17, -51, -93, -60, -128, -96, 88, -126, -21, 38, 14, 71, 105, 63, 100, 4, 92, -72};
	
	private static final String ALGORITHM_NAME_SHA256 = "SHA-256";
	private static final String ALGORITHM_NAME_SHA512 = "SHA-512";
	
	private static ConnectionProvider connectionProvider;

	@SuppressWarnings("unused")
	private MessageDigestHasher cut;
	private Connection rabbitConn;
	private static MinioClient minio;
	private static MinioStore minioStore;
	private static MinioTestHelper minioHelper;
	private Channel channelForTest;
	private QueueConfiguration queueConfig;
	
	private LinkedBlockingQueue<DbUpdate> dbUpdateMessages;
	
	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
		ConsulConfiguration consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		connectionProvider = new ConnectionProvider(consulConfig, new VaultIntegrationCredentials(Approle.integration), consulConfig.overrideVirtualBoxAddress());
		
		assertThat(connectionProvider, is(notNullValue()));

		minio = connectionProvider.getMinioClient();
		minioHelper = new MinioTestHelper(minio);
		minioStore = new MinioStore(minio, MinioConfiguration.integrationTestBuckets());

		minioStore.createBuckets();
		uploadTestImage();
	}

	private static void uploadTestImage() throws Exception {
		minioStore.storeImage(Paths.get("src\\test\\resources\\images\\", IMAGE_AUTUMN), IMAGE_AUTUMN_UUID);
	}

	@AfterAll
	public static void tearDownAfterClass() throws Exception {
		minioHelper.clearBucket(TEST_BUCKET_NAME);
		minio.removeBucket(RemoveBucketArgs.builder().bucket(TEST_BUCKET_NAME).build());
	}

	@BeforeEach
	public void setUp() throws Exception {
		ConnectionFactory connFactory = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.integration);
		assertThat(connFactory, is(notNullValue()));
		
		rabbitConn = connFactory.newConnection();
		ConsulClient consul = connectionProvider.getConsulClient();
		
		Channel channel = rabbitConn.createChannel();
		channelForTest = rabbitConn.createChannel();
		
		queueConfig = new QueueConfiguration(channel, true);
		
		cut = new MessageDigestHasher(rabbitConn, consul,
				new MinioStore(minio, MinioConfiguration.integrationTestBuckets()), queueConfig);
		
		dbUpdateMessages = new LinkedBlockingQueue<DbUpdate>();
		
		channel.basicConsume(queueConfig.getQueueName(ConfiguredQueues.persistence), new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
				DbUpdate message = DbUpdate.parseFrom(body);
				dbUpdateMessages.add(message);
			}
		});
	}
	
	
	private Path getClassPathFile(String fileName) throws URISyntaxException {
		return Paths.get(ClassLoader.getSystemResource("images/"+fileName).toURI());
	}
	
	private void sendFileProcessMessage(Path image, UUID imageId, boolean hasThumbnail) throws IOException {
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(Collections.emptyMap()).build();
		
		Builder messageBuilder = FileLoad.newBuilder().setGenerateThumbnail(!hasThumbnail).addMissingHash("SHA-256").addMissingHash("SHA-512")
				.setImageId(imageId.toString());
		messageBuilder.getImagePathBuilder().setAnchor(ANCHOR).setRelativePath(image.toString());

		channelForTest.basicPublish(queueConfig.getExchangeName(ConfiguredExchanges.loader), "", props, messageBuilder.build().toByteArray());
	}

	private byte[] getHashFromMessage(DbUpdate message, String hashName) {
		Map<String, ByteString> hashes = message.getHashMap();
		return hashes.get(hashName).toByteArray();
	}

	@AfterEach
	public void tearDown() throws Exception {
		queueConfig.deleteAllQueues();
		
		if (Objects.nonNull(rabbitConn)) {
			rabbitConn.close();
		}
		
		Morphium dbClient = connectionProvider.getMorphiumClient(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);
		dbClient.clearCachefor(ImageMetaData.class);
		dbClient.dropCollection(ImageMetaData.class);
	}
	
	private Callable<Integer> getQueueSize(LinkedBlockingQueue<?> queue) {
		return new Callable<Integer>() {
			public Integer call() {
				return queue.size();
			}
		};
	}

	@Test
	public void hashResponseIsReceived() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), IMAGE_AUTUMN_UUID, true);
		
		Awaitility.await().atMost(10, TimeUnit.SECONDS).until(getQueueSize(dbUpdateMessages), is(1));
	}
	
	@Test
	public void sha256IsCorrect() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), IMAGE_AUTUMN_UUID, true);
		DbUpdate message = dbUpdateMessages.take();
		
		assertThat(getHashFromMessage(message, ALGORITHM_NAME_SHA256), is(AUTUMN_SHA256));
	}

	@Test
	public void sha512IsCorrect() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), IMAGE_AUTUMN_UUID, true);
		DbUpdate message = dbUpdateMessages.take();

		assertThat(getHashFromMessage(message, ALGORITHM_NAME_SHA512), is(AUTUMN_SHA512));
	}
}
