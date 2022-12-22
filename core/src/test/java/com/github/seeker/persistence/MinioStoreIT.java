package com.github.seeker.persistence;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.MinioConfiguration;
import com.github.seeker.configuration.MinioConfiguration.BucketKey;

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
	private static final String IMAGE_ROAD_FAR = "road-far.jpg";

	private static Map<BucketKey, String> buckets;
	private static MinioStore sut;
	private static MinioClient minioClient;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ConfigurationBuilder configBuilder = new ConfigurationBuilder();
		ConsulConfiguration consulConfig = configBuilder.getConsulConfiguration();
		
		ConnectionProvider connectionProvider = new ConnectionProvider(consulConfig, configBuilder.getVaultCredentials(), consulConfig.overrideVirtualBoxAddress());
		minioClient = connectionProvider.getMinioClient();
		
		buckets = MinioConfiguration.integrationTestBuckets();
		sut = new MinioStore(minioClient, MinioConfiguration.integrationTestBuckets());
		sut.createBuckets();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Map<BucketKey, String> buckets = MinioConfiguration.integrationTestBuckets();

		for (String bucket : buckets.values()) {
			emptyBucket(bucket);
			minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
		}
	}

	private static void emptyBucket(String bucketName) throws Exception {
		Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).build());

		for (Result<Item> object : objects) {
			String objectName = object.get().objectName();

			minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
		}

	}

	@Before
	public void setUp() throws Exception {
		Map<BucketKey, String> buckets = MinioConfiguration.integrationTestBuckets();

		for (String bucket : buckets.values()) {
			emptyBucket(bucket);
		}

		sut.storeImage(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_ROAD_FAR), IMAGE_ROAD_FAR_UUID);
	}

	@Test
	public void testSetupHasExisitingImage() throws Exception {
		StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder().bucket(buckets.get(BucketKey.Image))
				.object(IMAGE_ROAD_FAR_UUID.toString() + ".jpg").build());

		assertThat(stat.size(), is(901202L));
	}

	@Test
	public void storeNewImage() throws Exception {
		Map<BucketKey, String> buckets = MinioConfiguration.integrationTestBuckets();
		sut.storeImage(Paths.get("..\\node\\src\\test\\resources\\images\\", IMAGE_AUTUMN), IMAGE_AUTUMN_UUID);

		StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder().bucket(buckets.get(BucketKey.Image))
				.object(IMAGE_AUTUMN_UUID.toString() + ".jpg").build());
		
		assertThat(stat.size(), is(312832L));
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
}
