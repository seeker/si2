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
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.MinioConfiguration;
import com.github.seeker.configuration.MinioConfiguration.BucketKey;
import com.github.seeker.persistence.document.ImageMetaData;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;

public class MinioStore {
	private static final Logger LOGGER = LoggerFactory.getLogger(MinioStore.class);

	private static final String PREFIX_IMAGE = "image";
	private static final String PREFIX_THUMBNAIL = "thumb";
	private static final String PREFIX_PREPROCESSED = "preprocessed";

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

	public void storeImage(Path path, UUID imageID) throws MinioPersistenceException {
		this.storeImage(path.toString(), imageID);
	}

	public void storeImage(String path, UUID imageID) throws MinioPersistenceException {

		try {
			client.uploadObject(UploadObjectArgs.builder().bucket(this.bucketNames.get(BucketKey.Si2)).object(uuidToObjectName(PREFIX_IMAGE, imageID))
					.filename(path).build());
		} catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
				| NoSuchAlgorithmException | ServerException | XmlParserException | IllegalArgumentException | IOException e) {
			throw new MinioPersistenceException(e);
		}

	}

	private String uuidToObjectName(String pathPrefix, UUID uuid) {
		return pathPrefix + "/" + uuid.toString() + OBJECT_ID_SUFFIX;
	}

	private String bucketName(BucketKey key) {
		return this.bucketNames.get(key);
	}

	public InputStream getImage(UUID uuid) throws MinioPersistenceException {
		try {
			return client.getObject(GetObjectArgs.builder().bucket(bucketNames.get(BucketKey.Si2)).object(uuidToObjectName(PREFIX_IMAGE, uuid)).build());
		} catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
				| NoSuchAlgorithmException | ServerException | XmlParserException | IllegalArgumentException | IOException e) {
			throw new MinioPersistenceException(e);
		}
	}

	public void deleteImage(UUID imageId) throws MinioPersistenceException {
		try {
			client.removeObject(RemoveObjectArgs.builder().bucket(bucketName(BucketKey.Si2)).object(uuidToObjectName(PREFIX_IMAGE, imageId)).build());
		} catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
				| NoSuchAlgorithmException | ServerException | XmlParserException | IllegalArgumentException | IOException e) {
			throw new MinioPersistenceException(e);
		}
	}

	public boolean imageExisits(UUID imageId) throws MinioPersistenceException {
		try {
		StatObjectResponse stat = client.statObject(
				StatObjectArgs.builder().bucket(bucketName(BucketKey.Si2))
						.object(uuidToObjectName(PREFIX_IMAGE, imageId)).build());

		return null != stat;
		}catch (ErrorResponseException e) {
			if ("NoSuchKey".equals(e.errorResponse().code())) {
				return false;
			} else {
				throw new MinioPersistenceException(e);
			}
		} catch (InvalidKeyException | InsufficientDataException | InternalException | InvalidResponseException | NoSuchAlgorithmException | ServerException
				| XmlParserException | IllegalArgumentException | IOException e) {
			throw new MinioPersistenceException(e);
		}
	}

	public void deleteImages(Iterator<ImageMetaData> imagesToDelete) {
		Iterator<DeleteObject> metaToDelete = new Iterator<DeleteObject>() {
			@Override
			public boolean hasNext() {
				return imagesToDelete.hasNext();
			}

			@Override
			public DeleteObject next() {
				return new DeleteObject(uuidToObjectName(PREFIX_IMAGE, imagesToDelete.next().getImageId()));
			}
		};

		Iterable<DeleteObject> deleteIterable = new Iterable<DeleteObject>() {
			@Override
			public Iterator<DeleteObject> iterator() {
				return metaToDelete;
			}
		};

		Iterable<Result<DeleteError>> result = client.removeObjects(
				RemoveObjectsArgs.builder().bucket(bucketName(BucketKey.Si2)).objects(deleteIterable).build());

		result.forEach(error -> {
			try {
				LOGGER.warn("Failed to delete {}: {}", error.get().objectName(), error.get().message());
			} catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException | InsufficientDataException | InternalException
					| InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
	}

	private void streamToObject(UUID imageId, InputStream imageStream, String prefix) throws MinioPersistenceException {
		try {
			client.putObject(PutObjectArgs.builder().bucket(bucketName(BucketKey.Si2)).object(uuidToObjectName(prefix, imageId))
					.stream(imageStream, -1, 5242880).build());
		} catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
				| NoSuchAlgorithmException | ServerException | XmlParserException | IllegalArgumentException | IOException e) {
			throw new MinioPersistenceException(e);
		}
	}

	private InputStream objectToStream(UUID imageId, String prefix) throws MinioPersistenceException {
		try {
			return client.getObject(GetObjectArgs.builder().bucket(bucketName(BucketKey.Si2)).object(uuidToObjectName(prefix, imageId)).build());
		} catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
				| NoSuchAlgorithmException | ServerException | XmlParserException | IllegalArgumentException | IOException e) {
			throw new MinioPersistenceException(e);
		}
	}

	public void storeThumbnail(UUID imageId, InputStream imageStream) throws MinioPersistenceException {
		streamToObject(imageId, imageStream, PREFIX_THUMBNAIL);
	}

	public InputStream getThumbnail(UUID imageId) throws MinioPersistenceException {
		return objectToStream(imageId, PREFIX_THUMBNAIL);
	}

	public void storePreProcessedImage(UUID imageId, InputStream imageStream) throws MinioPersistenceException {
		streamToObject(imageId, imageStream, PREFIX_PREPROCESSED);
	}

	public InputStream getPreProcessedImage(UUID imageId) throws MinioPersistenceException {
		return objectToStream(imageId, PREFIX_PREPROCESSED);
	}
}
