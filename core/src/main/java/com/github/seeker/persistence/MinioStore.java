/* The MIT License (MIT)
 * Copyright (c) 2022 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.MinioConfiguration;
import com.github.seeker.configuration.MinioConfiguration.BucketKey;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;

public class MinioStore {
	private static Logger LOGGER = LoggerFactory.getLogger(MinioStore.class);

	// This is to work around that browsers do not display images without the
	// correct extension, despite the Content Type: image/jpeg
	private static final String OBJECT_ID_SUFFIX = ".jpg";

	private final MinioClient client;
	private final Map<BucketKey, String> bucketNames;

	public MinioStore(MinioClient client, Map<BucketKey, String> bucketNames) {
		this.client = client;
		this.bucketNames = bucketNames;

		// Prevent timeout when processing large files
		client.setTimeout(TimeUnit.MINUTES.toMillis(5), TimeUnit.MINUTES.toMillis(5), TimeUnit.MINUTES.toMillis(5));
	}

	public void createBuckets() {
		for (BucketKey key : BucketKey.values()) {
			MinioConfiguration.createBucket(client, bucketNames.get(key));
		}
	}

	public void storeImage(Path path, UUID imageID) throws InvalidKeyException, ErrorResponseException,
			InsufficientDataException, InternalException, InvalidResponseException, NoSuchAlgorithmException,
			ServerException, XmlParserException, IllegalArgumentException, IOException {
		this.storeImage(path.toString(), imageID);
	}

	public void storeImage(String path, UUID imageID) throws InvalidKeyException, ErrorResponseException,
			InsufficientDataException, InternalException, InvalidResponseException, NoSuchAlgorithmException,
			ServerException, XmlParserException, IllegalArgumentException, IOException {
		client.uploadObject(UploadObjectArgs.builder().bucket(this.bucketNames.get(BucketKey.Image))
				.object(uuidToObjectName(imageID)).filename(path).build());
	}

	private String uuidToObjectName(UUID uuid) {
		return uuid.toString() + OBJECT_ID_SUFFIX;
	}

	public InputStream getImage(UUID uuid) throws InvalidKeyException, ErrorResponseException,
			InsufficientDataException, InternalException, InvalidResponseException, NoSuchAlgorithmException,
			ServerException, XmlParserException, IllegalArgumentException, IOException {
		return client.getObject(GetObjectArgs.builder().bucket(bucketNames.get(BucketKey.Image))
				.object(uuidToObjectName(uuid))
				.build());
	}
}
