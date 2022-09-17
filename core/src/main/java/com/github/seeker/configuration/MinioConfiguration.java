/* The MIT License (MIT)
 * Copyright (c) 2022 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

public class MinioConfiguration {
	/**
	 * Minio bucket for images that need to be processed
	 */
	public static String IMAGE_BUCKET = "si2-images";
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
