package com.github.seeker.app;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.github.seeker.messaging.proto.DbUpdateOuterClass.UpdateType;
import com.github.seeker.messaging.proto.FileLoadOuterClass.FileLoad;
import com.github.seeker.persistence.MinioStore;
import com.github.seeker.persistence.document.ImageMetaData;
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
public class ImageResizerIT {
	private static final int AWAIT_TIMEOUT_SECONDS = 5;
	private static final String INTEGRATION_BUCKET = MinioConfiguration.integrationTestBuckets().get(BucketKey.Si2);
	
	private static final String ANCHOR = "testimages";
	
	private static final String IMAGE_AUTUMN = "autumn.jpg";
	private static final UUID IMAGE_AUTUMN_UUID = UUID.randomUUID();

	private static ConnectionProvider connectionProvider;
	private static MinioClient minio;
	private static MinioStore minioStore;
	private static MinioTestHelper minioHelper;

	@SuppressWarnings("unused")
	private ImageResizer cut;
	private Connection rabbitConn;
	private Channel channelForTest;
	private QueueConfiguration queueConfig;
	private ConsulClient consul;
	
	private LinkedBlockingQueue<Map<String, Object>> dbMessageHeaders;
	private LinkedBlockingQueue<Map<String, Object>> preprocessedMessageHeaders;
	private LinkedBlockingQueue<DbUpdate> dbMessage;
	private LinkedBlockingQueue<FileLoad> preprocessedMessage;
	
	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
		ConsulConfiguration consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		connectionProvider = new ConnectionProvider(consulConfig, new VaultIntegrationCredentials(Approle.integration), consulConfig.overrideVirtualBoxAddress());
		
		assertThat(connectionProvider, is(notNullValue()));

		minio = connectionProvider.getMinioClient();
		minioHelper = new MinioTestHelper(minio);

		minioStore = new MinioStore(minio, MinioConfiguration.integrationTestBuckets());
		minioStore.createBuckets();
		minioStore.storeImage(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_AUTUMN),
				IMAGE_AUTUMN_UUID);
	}

	@AfterAll
	public static void tearDownAfterClass() throws Exception {
		minioHelper.clearBucket(INTEGRATION_BUCKET);
		minio.removeBucket(RemoveBucketArgs.builder().bucket(INTEGRATION_BUCKET).build());
	}

	@BeforeEach
	public void setUp() throws Exception {
		ConnectionFactory connFactory = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.integration);
		assertThat(connFactory, is(notNullValue()));
		
		rabbitConn = connFactory.newConnection();
		consul = connectionProvider.getConsulClient();
		
		Channel channel = rabbitConn.createChannel();
		channelForTest = rabbitConn.createChannel();
		
		queueConfig = new QueueConfiguration(channel, true);
		
		// Uploaded autumn image here

		cut = new ImageResizer(rabbitConn, consul, queueConfig,
				new MinioStore(minio, MinioConfiguration.integrationTestBuckets()));
		
		dbMessage = new LinkedBlockingQueue<DbUpdate>();
		preprocessedMessage = new LinkedBlockingQueue<FileLoad>();
		dbMessageHeaders = new LinkedBlockingQueue<Map<String,Object>>();
		preprocessedMessageHeaders = new LinkedBlockingQueue<Map<String,Object>>();
		
		channel.basicConsume(queueConfig.getQueueName(ConfiguredQueues.persistence), new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {

				dbMessageHeaders.add(properties.getHeaders());
				dbMessage.add(DbUpdate.parseFrom(body));
			}
		});
		
		channel.basicConsume(queueConfig.getQueueName(ConfiguredQueues.filePreProcessed), new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
		
				preprocessedMessageHeaders.add(properties.getHeaders());
				preprocessedMessage.add(FileLoad.parseFrom(body));
			}
		});
	}
	
	
	private Path getClassPathFile(String fileName) throws URISyntaxException {
		return Paths.get(ClassLoader.getSystemResource("images/"+fileName).toURI());
	}
	
	private void sendFileProcessMessage(Path image, UUID imageId, boolean hasThumbnail) throws IOException {
		sendFileProcessMessage(image, imageId, hasThumbnail, false);
	}

	private void sendFileProcessMessage(Path image, UUID imageId, boolean hasThumbnail, boolean recreateOnly) throws IOException {
		FileLoad.Builder builder = FileLoad.newBuilder();
		builder.setImageId(imageId.toString());
		builder.getImagePathBuilder().setAnchor(ANCHOR).setRelativePath(image.toString());
		builder.setGenerateThumbnail(!hasThumbnail);
		builder.addMissingHash("SHA-256").addMissingHash("SHA-512");
		
		if (recreateOnly) {
			builder.setRecreateThumbnail(true);
		}
		
		channelForTest.basicPublish(queueConfig.getExchangeName(ConfiguredExchanges.loader), "", null, builder.build().toByteArray());
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
	public void thumbnailGeneratedWhenMissing() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), IMAGE_AUTUMN_UUID, false);
		
		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(getQueueSize(dbMessage), is(1));

		DbUpdate message = dbMessage.take();

		assertThat(message.getUpdateType(), is(UpdateType.UPDATE_TYPE_THUMBNAIL));
	}
	
	@Test
	public void thumbnailNotGeneratedWhenPresent() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), IMAGE_AUTUMN_UUID, true);
		
		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(getQueueSize(preprocessedMessage), is(1));
		Awaitility.await().pollDelay(1, TimeUnit.SECONDS).atMost(AWAIT_TIMEOUT_SECONDS + 1, TimeUnit.SECONDS).until(getQueueSize(dbMessage), is(0));
	}
	
	@Test
	public void PreprocessResponseIsReceived() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), IMAGE_AUTUMN_UUID, true);
		
		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(getQueueSize(preprocessedMessage), is(1));
	}
	
	@Test
	public void thumbnailMessageContainsThumbnailSize() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), IMAGE_AUTUMN_UUID, false);
		
		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(getQueueSize(dbMessage), is(1));

		int thumbnailSize = (int) consul.getKvAsLong("config/general/thumbnail-size");
		assertThat(dbMessage.take().getThumbnailSize(), is(thumbnailSize));
	}

	@Test
	public void thumbnailIsStoredInBucket() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), IMAGE_AUTUMN_UUID, false);

		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(getQueueSize(dbMessage), is(1));
		
		InputStream thumb = minioStore.getThumbnail(IMAGE_AUTUMN_UUID);
		assertThat(thumb, is(notNullValue()));
	}

	@Test
	public void onlyRecreateThumbnailGeneratesThumbnail() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), IMAGE_AUTUMN_UUID, true, true);

		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(getQueueSize(dbMessageHeaders), is(1));

		InputStream thumb = minioStore.getThumbnail(IMAGE_AUTUMN_UUID);
		assertThat(thumb, is(notNullValue()));
	}

	@Test
	public void onlyRecreateThumbnailNoPreprocessedMessage() throws Exception {
		sendFileProcessMessage(getClassPathFile(IMAGE_AUTUMN), IMAGE_AUTUMN_UUID, true, true);

		Awaitility.await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(getQueueSize(dbMessage), is(1));
		Awaitility.await().pollDelay(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).until(getQueueSize(preprocessedMessage), is(0));
	}
}
