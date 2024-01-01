/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.processor;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.MinioConfiguration;
import com.github.seeker.configuration.MinioConfiguration.BucketKey;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredExchanges;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.helpers.MinioTestHelper;
import com.github.seeker.messaging.proto.FileLoadOuterClass.FileLoad;
import com.github.seeker.messaging.proto.ImagePathOuterClass.ImagePath;
import com.github.seeker.persistence.MinioStore;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.RateLimiter;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import de.caluga.morphium.Morphium;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;

public class FileToQueueVistorIT {
	private static final Duration timeout = Duration.ofSeconds(5);
	private static final String ANCHOR = "walk";
	private static final String TEST_BUCKET_NAME = MinioConfiguration.integrationTestBuckets().get(BucketKey.Si2);
	private static final String APPLE_FILENAME = "apple.jpg";
	private static final String ORANGE_FILENAME = "orange.png";
	private static final String CHERRY_FILENAME = "cherry.gif";
	private static final String GIRAFFE_FILENAME = "giraffe.txt";

	private static final byte[] APPLE_DATA = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 };
	private static final byte[] ORANGE_DATA = { 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };
	private static final byte[] CHERRY_DATA = { 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30 };

	private static final List<String> requiredHashes = ImmutableList.of("sha256", "sha512");

	private static MongoDbMapper mapper;
	private static MinioStore minio;
	private static MinioClient minioClient;
	private static ConnectionFactory rabbitConnFactory;
	private static Morphium morphium;
	private static MinioTestHelper minioTestHelper;

	private FileToQueueVistor cut;
	private Connection rabbitConn;

	private Path fileWalkRoot;

	private Map<String, FileLoad> messageData;
	private Map<String, Map<String, Object>> messageHeader;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ConfigurationBuilder config = new ConfigurationBuilder();
		ConnectionProvider connProv = new ConnectionProvider(config.getConsulConfiguration(), config.getVaultCredentials(), true);

		mapper = connProv.getIntegrationMongoDbMapper();
		minioClient = connProv.getMinioClient();
		minio = new MinioStore(minioClient, MinioConfiguration.integrationTestBuckets());
		rabbitConnFactory = connProv.getRabbitMQConnectionFactory(RabbitMqRole.integration);
		morphium = connProv.getMorphiumClient(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);

		minioTestHelper = new MinioTestHelper(minioClient);
		minio.createBuckets();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		morphium.close();
		minioClient.removeBucket(RemoveBucketArgs.builder().bucket(TEST_BUCKET_NAME).build());
	}

	@Before
	public void setUp() throws Exception {
		rabbitConn = rabbitConnFactory.newConnection();
		QueueConfiguration queueConfig = new QueueConfiguration(rabbitConn.createChannel(), true);

		messageData = new HashMap<>();
		messageHeader = new HashMap<>();

		Channel channel = rabbitConn.createChannel();
		String queue = channel.queueDeclare().getQueue();
		channel.queueBind(queue, queueConfig.getExchangeName(ConfiguredExchanges.loader), "");
		channel.basicConsume(queue, true, new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
				Map<String, Object> headers = properties.getHeaders();

				FileLoad message = FileLoad.parseFrom(body);
				ImagePath imagePath = message.getImagePath();

				String relativePath = imagePath.getRelativePath();

				messageData.put(relativePath, message);
				messageHeader.put(relativePath, headers);
			}
		});

		fileWalkRoot = Files.createTempDirectory("si2-"+ this.getClass().getCanonicalName());
		setUpTestFileSystem();

		RateLimiter rateLimiter = RateLimiter.create(50);

		cut = new FileToQueueVistor(rabbitConn.createChannel(), ANCHOR, fileWalkRoot, mapper, minio, requiredHashes,
				queueConfig.getExchangeName(ConfiguredExchanges.loader));
	}

	@After
	public void tearDown() throws Exception {
		rabbitConn.close();
		morphium.dropCollection(ImageMetaData.class);
		minioTestHelper.clearBucket(TEST_BUCKET_NAME);
	}

	private void setUpTestFileSystem() throws IOException {
		Files.createDirectories(fileWalkRoot);

		Files.write(fileWalkRoot.resolve(APPLE_FILENAME), APPLE_DATA);
		Files.write(fileWalkRoot.resolve(ORANGE_FILENAME), ORANGE_DATA);
		Files.write(fileWalkRoot.resolve(CHERRY_FILENAME), CHERRY_DATA);

		Files.createFile(fileWalkRoot.resolve(GIRAFFE_FILENAME));
	}

	@Test
	public void rootPathExists() throws Exception {
		assertThat(Files.exists(fileWalkRoot), is(true));
	}

	@Test
	public void filesAreQueuedForProcessing() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageData::size, is(3));
	}

	@Test
	public void onlyImagesQueued() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageData::size, is(3));

		assertThat(messageData.keySet(), not(hasItem(GIRAFFE_FILENAME)));
	}

	@Test
	public void allImagesQueued() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageData::size, is(3));

		assertThat(messageData.keySet(), hasItems(APPLE_FILENAME, ORANGE_FILENAME, CHERRY_FILENAME));
	}

	@Test
	public void imageUUIDSentInMessage() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageData::size, is(3));

		ImageMetaData meta = mapper.getImageMetadata(ANCHOR, APPLE_FILENAME);
		assertThat(UUID.fromString(messageData.get(APPLE_FILENAME).getImageId()), is(meta.getImageId()));
	}

	@Test
	public void missingDigestHashInMessage() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(messageData.get(APPLE_FILENAME).getMissingHashList(), hasItems("sha256", "sha512"));
	}

	@Test
	public void missingCustomHashInMessage() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(messageData.get(APPLE_FILENAME).getMissingCustomHashList(), hasItem("phash"));
	}

	@Test
	public void anchorInMessage() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(messageData.get(APPLE_FILENAME).getImagePath().getAnchor(), is(ANCHOR));
	}

	@Test
	public void anchorRelativePathInMessage() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(messageData.get(APPLE_FILENAME).getImagePath().getRelativePath(), is(APPLE_FILENAME));
	}

	@Test
	public void thumbnailFoundInMessage() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(messageData.get(APPLE_FILENAME).getGenerateThumbnail(), is(true));
	}

	@Test
	public void setFlagToNotGenerateThumbnails() throws Exception {
		cut.setGenerateThumbnails(false);
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(messageData.get(APPLE_FILENAME).getGenerateThumbnail(), is(false));
	}

	private Callable<ImageMetaData> getImageMetadata(String anchor, String relativePath) {
		return new Callable<ImageMetaData>() {
			public ImageMetaData call() {
				return mapper.getImageMetadata(anchor, relativePath);
			}
		};
	}

	private Callable<StatObjectResponse> getStatObject(StatObjectArgs args) {
		return new Callable<StatObjectResponse>() {
			public StatObjectResponse call() throws InvalidKeyException, ErrorResponseException, InsufficientDataException, InternalException,
					InvalidResponseException, NoSuchAlgorithmException, ServerException, XmlParserException, IOException {
				return minioClient.statObject(args);
			}
		};
	}

	@Test
	public void imageMetadataCreated() throws Exception {
		cut.setGenerateThumbnails(false);
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(getImageMetadata(ANCHOR, APPLE_FILENAME), is(notNullValue()));
	}

	@Test
	public void imageUploadedToBucket() throws Exception {
		cut.setGenerateThumbnails(false);
		Files.walkFileTree(fileWalkRoot, cut);
		
		Awaitility.await().atMost(timeout).until(getImageMetadata(ANCHOR, APPLE_FILENAME), is(notNullValue()));
		ImageMetaData meta = mapper.getImageMetadata(ANCHOR, APPLE_FILENAME);

		StatObjectArgs args = StatObjectArgs.builder().bucket(MinioConfiguration.integrationTestBuckets().get(BucketKey.Si2))
				.object("image/" + meta.getImageId().toString() + ".jpg").build();

		Awaitility.await().atMost(timeout).until(getStatObject(args), is(notNullValue()));

		assertThat(minioClient.statObject(args).size(), is(11L));
	}
}
