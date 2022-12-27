/* The MIT License (MIT)
 * Copyright (c) 2022 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.helpers;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.MinioConfiguration.BucketKey;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;

/**
 * Helper class for test using Minio
 */
public class MinioTestHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger(MinioTestHelper.class);
	private final MinioClient minio;

	public MinioTestHelper(MinioClient minio) {
		this.minio = minio;
	}

	/**
	 * Delete all objects in a bucket without deleting the bucket itself.
	 */
	public void clearBucket(String bucketName) {
		Iterable<Result<Item>> objects = minio.listObjects(ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());
		List<DeleteObject> toDelete = new LinkedList<>();

		objects.forEach(t -> {
			try {
				toDelete.add(new DeleteObject(t.get().objectName()));
			} catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException | InsufficientDataException | InternalException
					| InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException | IOException e) {
				LOGGER.error("Failed to create object delet request due to error: {}", e.getMessage());
			}
		});

		Iterable<Result<DeleteError>> results = minio.removeObjects(RemoveObjectsArgs.builder().bucket(bucketName).objects(toDelete).build());

		results.forEach(t -> {
			try {
				DeleteError error = t.get();
				LOGGER.error("Failed to delete object {} from bucket {} due to error {}", error.objectName(), error.bucketName(), error.message());
			} catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException | InsufficientDataException | InternalException
					| InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException | IOException e) {
				LOGGER.error("Failed to process object removal due to error: {}", e.getMessage());
			}
		});
	}

	/**
	 * Empties all buckets and deletes them.
	 * 
	 * @param buckets map containing buckets to delete
	 */
	public void deleteAllBuckets(Map<BucketKey, String> buckets) throws InvalidKeyException, ErrorResponseException,
			InsufficientDataException, InternalException, InvalidResponseException, NoSuchAlgorithmException,
			ServerException, XmlParserException, IllegalArgumentException, IOException {
		for (String bucket : buckets.values()) {
			clearBucket(bucket);
			minio.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
		}
	}
}
