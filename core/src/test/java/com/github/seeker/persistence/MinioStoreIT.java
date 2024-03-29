package com.github.seeker.persistence;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.MinioConfiguration;
import com.github.seeker.configuration.MinioConfiguration.BucketKey;
import com.github.seeker.persistence.document.ImageMetaData;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;

public class MinioStoreIT {
	private static final String IMAGE_AUTUMN = "autumn.jpg";
	private static final UUID IMAGE_AUTUMN_UUID = UUID.randomUUID();
	private static final UUID IMAGE_ROAD_FAR_UUID = UUID.randomUUID();
	private static final UUID IMAGE_ROAD_NEAR_UUID = UUID.randomUUID();
	private static final String IMAGE_ROAD_FAR = "road-far.jpg";
	private static final String IMAGE_ROAD_NEAR = "road-near.jpg";
	private static final long IMAGE_AUTUMN_SIZE = 312832L;
	private static final long IMAGE_ROAD_NEAR_SIZE = 820370L;
	private static final long IMAGE_ROAD_FAR_SIZE = 901202L;

	private static Map<BucketKey, String> buckets;
	private static MinioStore sut;
	private static MinioClient minioClient;

	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
		ConfigurationBuilder configBuilder = new ConfigurationBuilder();
		ConsulConfiguration consulConfig = configBuilder.getConsulConfiguration();
		
		ConnectionProvider connectionProvider = new ConnectionProvider(consulConfig, configBuilder.getVaultCredentials(), consulConfig.overrideVirtualBoxAddress());
		minioClient = connectionProvider.getMinioClient();
		
		buckets = MinioConfiguration.integrationTestBuckets();
		sut = new MinioStore(minioClient, MinioConfiguration.integrationTestBuckets());
		sut.createBuckets();
	}

	@AfterAll
	public static void tearDownAfterClass() throws Exception {
		Map<BucketKey, String> buckets = MinioConfiguration.integrationTestBuckets();

		for (String bucket : buckets.values()) {
			emptyBucket(bucket);
			minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
		}
	}

	private static void emptyBucket(String bucketName) throws Exception {
		Iterable<Result<Item>> objects = minioClient
				.listObjects(ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());

		for (Result<Item> object : objects) {
			String objectName = object.get().objectName();

			minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
		}

	}

	@BeforeEach
	public void setUp() throws Exception {
		Map<BucketKey, String> buckets = MinioConfiguration.integrationTestBuckets();

		for (String bucket : buckets.values()) {
			emptyBucket(bucket);
		}

		sut.storeImage(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_ROAD_FAR), IMAGE_ROAD_FAR_UUID);
		sut.storeThumbnail(IMAGE_AUTUMN_UUID, Files.newInputStream(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_AUTUMN)));
		sut.storePreProcessedImage(IMAGE_ROAD_NEAR_UUID, Files.newInputStream(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_ROAD_NEAR)));
	}

	@Test
	public void testSetupHasExisitingImage() throws Exception {
		StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder().bucket(buckets.get(BucketKey.Si2))
				.object("image/" + IMAGE_ROAD_FAR_UUID.toString() + ".jpg").build());

		assertThat(stat.size(), is(901202L));
		imageExisitsAtPrefix("image", IMAGE_ROAD_FAR_UUID, IMAGE_ROAD_FAR_SIZE);
	}

	@Test
	public void storeNewImage() throws Exception {
		Map<BucketKey, String> buckets = MinioConfiguration.integrationTestBuckets();
		sut.storeImage(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_AUTUMN), IMAGE_AUTUMN_UUID);

		imageExisitsAtPrefix("image", IMAGE_AUTUMN_UUID, IMAGE_AUTUMN_SIZE);
	}

	@Test
	public void getExistingImage() throws Exception {
		MessageDigest mdOriginal = MessageDigest.getInstance("SHA-256");
		MessageDigest mdMinio = MessageDigest.getInstance("SHA-256");

		InputStream isMinio = sut.getImage(IMAGE_ROAD_FAR_UUID);
		DigestInputStream disMinion = new DigestInputStream(isMinio, mdMinio);
		disMinion.transferTo(OutputStream.nullOutputStream());

		InputStream isOriginal = Files
				.newInputStream(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_ROAD_FAR));
		DigestInputStream disOriginal = new DigestInputStream(isOriginal, mdOriginal);
		disOriginal.transferTo(OutputStream.nullOutputStream());

		assertThat(mdOriginal.digest(), is(mdMinio.digest()));
	}

	@Test
	public void deleteExisitngImage() throws Exception {
		sut.deleteImage(IMAGE_ROAD_FAR_UUID);

		assertThat(sut.imageExisits(IMAGE_ROAD_FAR_UUID), is(false));
	}

	@Test
	public void imageExisits() throws Exception {
		assertThat(sut.imageExisits(IMAGE_ROAD_FAR_UUID), is(true));
	}

	@Test
	public void imageDoesNotExisit() throws Exception {
		assertThat(sut.imageExisits(IMAGE_AUTUMN_UUID), is(false));
	}

	@Test
	public void deleteMultipleImages() throws Exception {
		sut.storeImage(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_AUTUMN), IMAGE_AUTUMN_UUID);

		List<ImageMetaData> metaToDelete = new LinkedList<>();
		ImageMetaData autumn = new ImageMetaData();
		autumn.setImageId(IMAGE_AUTUMN_UUID);
		ImageMetaData road = new ImageMetaData();
		road.setImageId(IMAGE_ROAD_FAR_UUID);

		
		metaToDelete.add(autumn);
		metaToDelete.add(road);
		
		sut.deleteImages(metaToDelete.iterator());

		assertThat(sut.imageExisits(IMAGE_AUTUMN_UUID), is(false));
		assertThat(sut.imageExisits(IMAGE_ROAD_FAR_UUID), is(false));
	}

	@Test
	public void storeThumbnail() throws Exception {
		sut.storeThumbnail(IMAGE_AUTUMN_UUID, Files.newInputStream(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_AUTUMN)));

		imageExisitsAtPrefix("thumb", IMAGE_AUTUMN_UUID, IMAGE_AUTUMN_SIZE);
	}

	@Test
	public void getThumbnail() throws Exception {
		InputStream is = sut.getThumbnail(IMAGE_AUTUMN_UUID);

		assertThat(is.skip(Long.MAX_VALUE), is(312832L));
	}

	private void imageExisitsAtPrefix(String prefix, UUID imageId, long imageSize) throws Exception {
		StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder().bucket(buckets.get(BucketKey.Si2))
				.object(prefix + "/" + imageId.toString() + ".jpg").build());

		assertThat(stat.size(), is(imageSize));

	}

	@Test
	public void storePreProcessed() throws Exception {
		sut.storePreProcessedImage(IMAGE_AUTUMN_UUID, Files.newInputStream(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_AUTUMN)));

		imageExisitsAtPrefix("preprocessed", IMAGE_AUTUMN_UUID, IMAGE_AUTUMN_SIZE);
	}

	@Test
	public void getPreProcessed() throws Exception {
		InputStream is = sut.getPreProcessedImage(IMAGE_ROAD_NEAR_UUID);

		assertThat(is.skip(Long.MAX_VALUE), is(IMAGE_ROAD_NEAR_SIZE));
	}

	@Test
	public void moveCorruptedFileCreatesFile() throws Exception {
		sut.moveImageToCorrupted(IMAGE_ROAD_FAR_UUID);

		imageExisitsAtPrefix("corrupted", IMAGE_ROAD_FAR_UUID, IMAGE_ROAD_FAR_SIZE);
	}

	@Test
	public void corruptImageRemovedFromImages() throws Exception {
		sut.moveImageToCorrupted(IMAGE_ROAD_FAR_UUID);

		assertThat(sut.imageExisits(IMAGE_ROAD_FAR_UUID), is(false));
	}
}
