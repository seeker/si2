/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.processor;

import static org.awaitility.Awaitility.to;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredExchanges;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.MessageHeaderKeys;
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
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;

public class FileToQueueVistorIT {
	private static final Duration timeout = Duration.FIVE_SECONDS;
	private static final String ANCHOR = "walk";
	private static final String TEST_BUCKET_NAME = "integration-" + FileToQueueVistorIT.class.getSimpleName().toLowerCase();

	private static final String APPLE_FILENAME = "apple.jpg";
	private static final String ORANGE_FILENAME = "orange.png";
	private static final String CHERRY_FILENAME = "cherry.gif";
	private static final String GIRAFFE_FILENAME = "giraffe.txt";

	private static final byte[] APPLE_DATA = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 };
	private static final byte[] ORANGE_DATA = { 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };
	private static final byte[] CHERRY_DATA = { 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30 };

	private static final List<String> requiredHashes = ImmutableList.of("sha256", "sha512");

	private static MongoDbMapper mapper;
	private static MinioClient minio;
	private static ConnectionFactory rabbitConnFactory;
	private static Morphium morphium;

	private FileToQueueVistor cut;
	private Connection rabbitConn;

	private Path fileWalkRoot;

	private Map<String, byte[]> messageData;
	private Map<String, Map<String, Object>> messageHeader;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ConfigurationBuilder config = new ConfigurationBuilder();
		ConnectionProvider connProv = new ConnectionProvider(config.getConsulConfiguration(), config.getVaultCredentials(), true);

		mapper = connProv.getIntegrationMongoDbMapper();
		minio = connProv.getMinioClient();
		rabbitConnFactory = connProv.getRabbitMQConnectionFactory(RabbitMqRole.integration);
		morphium = connProv.getMorphiumClient(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);

		if (!minio.bucketExists(BucketExistsArgs.builder().bucket(TEST_BUCKET_NAME).build())) {
			minio.makeBucket(MakeBucketArgs.builder().bucket(TEST_BUCKET_NAME).build());
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		morphium.close();
		minio.removeBucket(RemoveBucketArgs.builder().bucket(TEST_BUCKET_NAME).build());
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
				String relativePath = headers.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH.toString()).toString();

				messageData.put(relativePath, body);
				messageHeader.put(relativePath, headers);
			}
		});

		fileWalkRoot = Files.createTempDirectory("si2-"+ this.getClass().getCanonicalName());
		setUpTestFileSystem();

		RateLimiter rateLimiter = RateLimiter.create(50);



		cut = new FileToQueueVistor(rabbitConn.createChannel(), rateLimiter, ANCHOR, fileWalkRoot, mapper, minio, TEST_BUCKET_NAME, requiredHashes,
				queueConfig.getExchangeName(ConfiguredExchanges.loader));
	}

	@After
	public void tearDown() throws Exception {
		rabbitConn.close();
		morphium.dropCollection(ImageMetaData.class);
		clearMinioBucket();
	}
	
	private void clearMinioBucket() throws Exception {
		Iterable<Result<Item>> objects = minio.listObjects(ListObjectsArgs.builder().bucket(TEST_BUCKET_NAME).build());
		List<DeleteObject> toDelete = new LinkedList<>();
		
		objects.forEach(t -> {try {
			toDelete.add(new DeleteObject(t.get().objectName()));
		} catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException | InsufficientDataException | InternalException
				| InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}});
		
		Iterable<Result<DeleteError>> results = minio.removeObjects(RemoveObjectsArgs.builder().bucket(TEST_BUCKET_NAME).objects(toDelete).build());
		
		results.forEach(t -> {
			try {
				System.out.println(t.get().message());
			} catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException | InsufficientDataException | InternalException
					| InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
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
	public void imageDataSentInMessage() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageData::size, is(3));

		assertThat(messageData.get(APPLE_FILENAME), is(APPLE_DATA));
	}

	@Test
	public void missingDigestHashInHeader() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(messageHeader.get(APPLE_FILENAME).get(MessageHeaderKeys.HASH_ALGORITHMS).toString(), is("sha256,sha512"));
	}

	@Test
	public void missingCustomHashInHeader() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(messageHeader.get(APPLE_FILENAME).get(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS).toString(), is("phash"));
	}

	@Test
	public void anchorInHeader() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(messageHeader.get(APPLE_FILENAME).get(MessageHeaderKeys.ANCHOR).toString(), is(ANCHOR));
	}

	@Test
	public void anchorRelativePathInHeader() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(messageHeader.get(APPLE_FILENAME).get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH).toString(), is(APPLE_FILENAME));
	}

	@Test
	public void thumbnailFoundInHeader() throws Exception {
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(Boolean.parseBoolean(messageHeader.get(APPLE_FILENAME).get(MessageHeaderKeys.THUMBNAIL_FOUND).toString()), is(false));
	}

	@Test
	public void setFlagToNotGenerateThumbnails() throws Exception {
		cut.setGenerateThumbnails(false);
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).until(messageHeader::size, is(3));

		assertThat(Boolean.parseBoolean(messageHeader.get(APPLE_FILENAME).get(MessageHeaderKeys.THUMBNAIL_FOUND).toString()), is(true));
	}

	@Test
	public void imageMetadataCreated() throws Exception {
		cut.setGenerateThumbnails(false);
		Files.walkFileTree(fileWalkRoot, cut);

		Awaitility.await().atMost(timeout).untilCall(to(mapper).getImageMetadata(ANCHOR, APPLE_FILENAME), is(notNullValue()));
	}

	@Test
	public void imageUploadedToBucket() throws Exception {
		cut.setGenerateThumbnails(false);
		Files.walkFileTree(fileWalkRoot, cut);
		
		Awaitility.await().atMost(timeout).untilCall(to(mapper).getImageMetadata(ANCHOR, APPLE_FILENAME), is(notNullValue()));
		ImageMetaData meta = mapper.getImageMetadata(ANCHOR, APPLE_FILENAME);

		StatObjectArgs args = StatObjectArgs.builder().bucket(TEST_BUCKET_NAME).object(meta.getImageId().toString()).build();
		Awaitility.await().atMost(timeout).untilCall(to(minio).statObject(args), is(notNullValue()));

		assertThat(minio.statObject(args).size(), is(11L));
	}
}
