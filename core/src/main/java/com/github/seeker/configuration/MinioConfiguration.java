/* The MIT License (MIT)
 * Copyright (c) 2022 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

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
}
