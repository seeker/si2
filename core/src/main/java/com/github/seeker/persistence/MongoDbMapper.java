/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.persistence.document.ImageMetaData;

import de.caluga.morphium.Morphium;
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
	 * Get metadata for an image
	 * @param anchor anchor for the image
	 * @param relativeAnchorPath relative path for the anchor to the image
	 * @return Image metadata if found, otherwise null
	 */
	public ImageMetaData getImageMetadata(String anchor, Path relativeAnchorPath) {
		return getImageMetadata(anchor, relativeAnchorPath.toString());
	}
	
	public List<ImageMetaData> getImageMetadata(int limit) {
		Query<ImageMetaData>  query = client.createQueryFor(ImageMetaData.class).limit(limit);
		
		return query.asList(); 
	}
	
	public List<ImageMetaData> getImageMetadata(int skip, int limit) {
		Query<ImageMetaData>  query = client.createQueryFor(ImageMetaData.class).skip(skip).limit(limit);
		
		return query.asList(); 
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
}
