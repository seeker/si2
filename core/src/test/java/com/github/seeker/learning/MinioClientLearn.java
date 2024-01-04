package com.github.seeker.learning;


import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.minio.messages.Tags;

public class MinioClientLearn {
	private static String TEST_BUCKET_NAME = "learning-test";
	private static MinioClient minioClient;

	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
		minioClient = MinioClient.builder().endpoint("http://127.0.0.1:9000").credentials("minioadmin", "minioadmin").build();
		minioClient.makeBucket(MakeBucketArgs.builder().bucket(TEST_BUCKET_NAME).build());
	}
	
	@AfterAll
	public static void tearDownAfterClass() throws Exception {
		Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder().bucket(TEST_BUCKET_NAME).build());
		List<DeleteObject> toDelete = new LinkedList<>();
		
		objects.forEach(t -> {try {
			toDelete.add(new DeleteObject(t.get().objectName()));
		} catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException | InsufficientDataException | InternalException
				| InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}});
		
		Iterable<Result<DeleteError>> results = minioClient.removeObjects(RemoveObjectsArgs.builder().bucket(TEST_BUCKET_NAME).objects(toDelete).build());
		
		results.forEach(t -> {
			try {
				System.out.println(t.get().message());
			} catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException | InsufficientDataException | InternalException
					| InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		
		minioClient.removeBucket(RemoveBucketArgs.builder().bucket(TEST_BUCKET_NAME).build());
	}

	@Test
	public void bucketCreated() throws Exception {
		assertTrue(minioClient.bucketExists(BucketExistsArgs.builder().bucket(TEST_BUCKET_NAME).build()));
	}
	
	@Test
	public void uploadObject() throws Exception {
		Map<String, String> tags = new HashMap<>();
		tags.put("image-type", "original");
		
		minioClient.uploadObject(
				UploadObjectArgs.builder()
				.bucket(TEST_BUCKET_NAME)
				.contentType("image/jpeg")
				.object("test-image.jpg")
				.tags(Tags.newObjectTags(tags))
				.filename("..\\node\\src\\test\\resources\\images\\autumn.jpg")
				.build()
				);
		
		minioClient.uploadObject(
				UploadObjectArgs.builder()
				.bucket(TEST_BUCKET_NAME)
				.contentType("image/jpeg")
				.object("test-image2.jpg")
				.tags(Tags.newObjectTags(tags))
				.filename("..\\node\\src\\test\\resources\\images\\road-far.jpg")
				.build()
				);
		
	}
	
	@Test
	public void uploadCorruptImage() throws Exception {
		Map<String, String> tags = new HashMap<>();
		tags.put("image-type", "original");

		
		minioClient.uploadObject(
				UploadObjectArgs.builder()
				.bucket(TEST_BUCKET_NAME)
				.contentType("image/jpeg")
				.object("corrupt.jpg")
				.tags(Tags.newObjectTags(tags))
				.filename("..\\node\\src\\test\\resources\\images\\corrupt.jpg")
				.build()
				);
		
	}
}
