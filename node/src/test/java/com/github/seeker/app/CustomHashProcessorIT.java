package com.github.seeker.app;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.protobuf.ByteString;
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
public class CustomHashProcessorIT {

	private static final String ANCHOR = "testimages";
	private static final String TEST_BUCKET_NAME = MinioConfiguration.integrationTestBuckets().get(BucketKey.Si2);
	
	private static final String IMAGE_ROAD_FAR = "road-far-pp.jpg";
	private static final UUID IMAGE_ROAD_FAR_UUID = UUID.randomUUID();
	private static final long IMAGE_ROAD_FAR_PHASH = 8792943954746078079L;

	private static ConnectionProvider connectionProvider;

	@SuppressWarnings("unused")
	private CustomHashProcessor cut;
	private Connection rabbitConn;
	private static MinioClient minio;
	private static MinioStore minioStore;
	private static MinioTestHelper minioHelper;
	private Channel channelForTest;
	private QueueConfiguration queueConfig;
	
	private LinkedBlockingQueue<DbUpdate> hashMessages;
	
	
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
		minioStore.storePreProcessedImage(IMAGE_ROAD_FAR_UUID, Files.newInputStream(Paths.get("src\\test\\resources\\images\\", IMAGE_ROAD_FAR)));
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
		cut = new CustomHashProcessor(channel, consul, minioStore, queueConfig);
		
		hashMessages = new LinkedBlockingQueue<DbUpdate>();
		
		
		channel.basicConsume(queueConfig.getQueueName(ConfiguredQueues.persistence), new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
				
				DbUpdate message = DbUpdate.parseFrom(body);

				hashMessages.add(message);
			}
		});
	}
	
	private Callable<Integer> numberOfHashMessages() {
		return new Callable<Integer>() {
			public Integer call() {
				return hashMessages.size();
			}
		};
	}
	
	private Path getClassPathFile(String fileName) throws URISyntaxException {
		return Paths.get(ClassLoader.getSystemResource("images/"+fileName).toURI());
	}
	
	private void sendFileProcessMessage(Path image, UUID imageId) throws IOException {
		Builder messageBuilder = FileLoad.newBuilder().setGenerateThumbnail(false).addMissingCustomHash("phash")
				.setImageId(imageId.toString());
		messageBuilder.getImagePathBuilder().setAnchor(ANCHOR).setRelativePath(image.toString());

		channelForTest.basicPublish("", queueConfig.getQueueName(ConfiguredQueues.filePreProcessed), null, messageBuilder.build().toByteArray());
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

	@Test
	public void hashResponseIsReceived() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_ROAD_FAR), IMAGE_ROAD_FAR_UUID);
		
		Awaitility.await().atMost(10, TimeUnit.SECONDS).until(numberOfHashMessages(), is(1));
	}
	
	@Test
	public void phashIsCorrect() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_ROAD_FAR), IMAGE_ROAD_FAR_UUID);
		Awaitility.await().atMost(10, TimeUnit.SECONDS).until(numberOfHashMessages(), is(1));
		DbUpdate message = hashMessages.take();
		ByteString hash = message.getHashMap().get("phash");
		ByteArrayDataInput dataIn = ByteStreams.newDataInput(hash.toByteArray());

		assertThat(dataIn.readLong(), is(IMAGE_ROAD_FAR_PHASH));
	}
}
