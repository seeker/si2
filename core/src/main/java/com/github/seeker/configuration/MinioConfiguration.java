/* The MIT License (MIT)
 * Copyright (c) 2022 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

public class MinioConfiguration {
	public static enum BucketKey {
		/**
		 * Bucket for SI2
		 */
		Si2
	}

	/**
	 * Create a {@link Map} with the bucket key and matching names.
	 * 
	 * @return a {@link Map} with the bucket names
	 */
	public static Map<BucketKey, String> productionBuckets() {
		HashMap<BucketKey, String> map = new HashMap<>();

		map.put(BucketKey.Si2, "si2");
		
		return map;
	}

	/**
	 * Create a {@link Map} with the bucket key and matching names for integration
	 * testing. The integration bucket names are production buckets prefixed with
	 * `integration-`.
	 * 
	 * @return a {@link Map} with the bucket names for integration testing
	 */
	public static Map<BucketKey, String> integrationTestBuckets() {
		Map<BucketKey, String> buckets = productionBuckets();

		buckets.forEach(new BiConsumer<BucketKey, String>() {

			@Override
			public void accept(BucketKey key, String bucketName) {
				buckets.put(key, "integration-" + bucketName);
			}
		});

		return buckets;
	}

	/**
	 * Minio bucket for thumbnails of loaded images
	 */
	public static String THUMBNAIL_BUCKET = "si2-thumbnails";
	/**
	 * Minio bucket for grayscale and resized images used for phash
	 */
	public static String PREPROCESSED_IMAGES_BUCKET = "si2-preprocessed";

	/**
	 * Try to create the bucket if it does not exist. Will rethrow any exception as
	 * a runtime exception!
	 * 
	 * @param minio      Client to use for bucket query and creation
	 * @param bucketName Name of the bucket to check and create
	 */
	public static void createBucket(MinioClient minio, String bucketName) {
		try {
			boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());

			if (!exists) {
				minio.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to create Minio bucket " + bucketName, e);
		}
	}
}
