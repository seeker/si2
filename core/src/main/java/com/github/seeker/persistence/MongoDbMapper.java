/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.persistence.document.FileLoaderJob;
import com.github.seeker.persistence.document.ImageMetaData;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.MorphiumIterator;
import de.caluga.morphium.query.Query;

public class MongoDbMapper {
	private static final Logger LOGGER = LoggerFactory.getLogger(MongoDbMapper.class);

	private final Morphium client;

	/**
	 * Create a mapper using the given {@link Morphium} instance.
	 * 
	 * @param client
	 *            to use for database access
	 */
	public MongoDbMapper(Morphium client) {
		this.client = client;
	}
	
	public void storeDocument(ImageMetaData meta) {
		client.store(meta);
	}
	
	/**
	 * Check if the given hash is stored for an image.
	 * 
	 * @param anchor anchor for the image
	 * @param relativeAnchorPath the anchor's relative path to the image
	 * @param hashName the name of the hash to check for
	 * @return true if a hash is found, else false
	 */
	public boolean hasHash(String anchor, Path relativeAnchorPath, String hashName) {
		Query<ImageMetaData>  query = client.createQueryFor(ImageMetaData.class).f("anchor").eq(anchor).f("path").eq(relativeAnchorPath.toString());
		ImageMetaData meta = query.get();
		
		if(meta == null) {
			return false;
		}
		
		return meta.getHashes().containsKey(hashName);
	}

	/**
	 * Check if any {@link ImageMetaData} has a thumbnail with the given image ID;
	 * @param imageId to check for
	 * @return true if there is at least one thumbnail with this ID
	 */
	public boolean hasImageId(String imageId) {
		Query<ImageMetaData>  query = client.createQueryFor(ImageMetaData.class).f("thumbnail.imageId").eq(UUID.fromString(imageId));
		ImageMetaData meta = query.get();
		
		if(meta == null) {
			return false;
		} else {
			return true;
		}
	}
	
	/**
	 * Get metadata for an image
	 * @param anchor anchor for the image
	 * @param relativeAnchorPath relative path for the anchor to the image
	 * @return Image metadata if found, otherwise null
	 */
	public ImageMetaData getImageMetadata(String anchor, Path relativeAnchorPath) {
		return getImageMetadata(anchor, relativeAnchorPath.toString());
	}
	
	private Query<ImageMetaData> filterQuery(Map<String, Object> searchParameters) {
		Query<ImageMetaData>  query = client.createQueryFor(ImageMetaData.class);
		
		for (Entry<String,Object> e: searchParameters.entrySet()) {
			query = query.f(e.getKey()).matches("^.*" + e.getValue() + ".*$");
		}
		
		return query;
	}
	
	public long getFilteredImageMetadataCount(Map<String, Object> searchParameters) {
		Query<ImageMetaData> query = filterQuery(searchParameters);
		
		return query.countAll();
	}
	
	public List<ImageMetaData> getImageMetadata(Map<String, Object> searchParameters, int skip, int limit) {
		Query<ImageMetaData> query = filterQuery(searchParameters);
		
		return query.skip(skip).limit(limit).asList();
	}
	
	public MorphiumIterator<ImageMetaData> getThumbnailsToResize(int thumbnailSize) {
		Query<ImageMetaData> query = client.createQueryFor(ImageMetaData.class).f("thumbnail.max_image_size").ne(thumbnailSize);
		return query.asIterable();
	}

	/**
	 * Return all entries that match the given filter. 
	 * @param searchParameters field / value pairs for filtering results, all fields are combined with AND
	 * @return all entries that match
	 */
	public List<ImageMetaData> getImageMetadata(Map<String, Object> searchParameters) {
		Query<ImageMetaData> query = filterQuery(searchParameters);
		
		return query.skip(0).limit(0).asList();
	}
	
	public long getImageMetadataCount() {
		return client.createQueryFor(ImageMetaData.class).countAll();
	}
	
	/**
	 * Get metadata for an image
	 * @param anchor anchor for the image
	 * @param relativeAnchorPath relative path for the anchor to the image
	 * @return Image metadata if found, otherwise null
	 */
	public ImageMetaData getImageMetadata(String anchor, String relativeAnchorPath) {
		Query<ImageMetaData>  query = client.createQueryFor(ImageMetaData.class).f("anchor").eq(anchor).f("path").eq(relativeAnchorPath);
		return query.get();
	}
	
	public List<ImageMetaData> getMetadataByHash(String hashName, byte[] hash) {
		Query<ImageMetaData>  query = client.createQueryFor(ImageMetaData.class).f("hashes."+ hashName +".hash").eq(hash);
		return query.asList();
	}

	/**
	 * Store a file load job.
	 * 
	 * @param job to store
	 */
	public void storeFileLoadJob(FileLoaderJob job) {
		client.store(job);
	}

	/**
	 * Return the first job for the given anchor that has not been completed, if
	 * any.
	 * 
	 * @param anchor to search for jobs
	 * @return the first open job or null if there are no jobs
	 */
	public FileLoaderJob getOpenFileLoadJobsForAnchor(String anchor) {
		Query<FileLoaderJob> query = client.createQueryFor(FileLoaderJob.class).f("anchor").eq(anchor).f("completed")
				.eq(false);
		return query.get();
	}

	/**
	 * Get all file load jobs.
	 * 
	 * @return a list of all file load jobs, if any
	 */
	public List<FileLoaderJob> getAllFileLoadJobs() {
		Query<FileLoaderJob> query = client.createQueryFor(FileLoaderJob.class);
		return query.asList();
	}
	
	/**
	 * Return all metadata that has a thumbnail, the requested message digests and
	 * custom hashes.
	 * 
	 * @return An iterator with metadata matching the query
	 */
	public MorphiumIterator<ImageMetaData> getProcessingCompletedMetadata(List<String> requiredHashes) {
		Query<ImageMetaData> query = client.createQueryFor(ImageMetaData.class).f("thumbnail").exists();

		for (String hash : requiredHashes) {
			query = query.f("hashes." + hash).exists();
		}

		return query.asIterable();
	}
}
