/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence.document;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import de.caluga.morphium.annotations.CreationTime;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.driver.MorphiumId;

/**
 * Stores metadata for an image.
 */
@CreationTime
@Entity(translateCamelCase = true)
public class ImageMetaData {
	@Id
	private MorphiumId id;
	/**
	 * Identify the source of the file. This value can be arbitrary.
	 */
	@Index
	private String anchor;

	/**
	 * The path of the file, relative to the anchor. Must be unique per anchor.
	 */
	@Index
	private String path;
	/**
	 * When this record was created
	 */
	@CreationTime
	private Date creationTime;
	/**
	 * Size of the file
	 */
	private long fileSize;
	/**
	 * Hashes of this file. Hash and the corresponding value.
	 */
	@Index
	private Map<String, Hash> hashes;
	
	/**
	 * Id of the thumbnail
	 */
	@Index
	private Thumbnail thumbnail;
	
	/**
	 * Tags assigned to this image
	 */
	@Index
	private List<String> tags;
	
	/**
	 * Unique identifier for an image, used for scaled images and thumbnails
	 */
	// FIXME make index unique
	@Index
	private UUID imageId;
	
	/**
	 * Create a new {@link ImageMetaData} with no hashes and an empty anchor and path.		
	 */
	public ImageMetaData() {
		this.hashes = new HashMap<String, Hash>();
		this.anchor = StringUtils.EMPTY;
		this.path = StringUtils.EMPTY;
		this.imageId = UUID.randomUUID();
	}

	public UUID getImageId() {
		return imageId;
	}

	public void setImageId(UUID imageId) {
		this.imageId = imageId;
	}

	public Map<String, Hash> getHashes() {
		return hashes;
	}

	public void setHashes(Map<String, Hash> hashes) {
		this.hashes = hashes;
	}

	public String getAnchor() {
		return anchor;
	}

	public void setAnchor(String anchor) {
		this.anchor = anchor;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public Thumbnail getThumbnail() {
		return thumbnail;
	}

	public void setThumbnailId(Thumbnail thumbnail) {
		this.thumbnail = thumbnail;
	}
	
	/**
	 * Is there a thumbnail available for this image?
	 * @return true if there is a thumbnail
	 */
	public boolean hasThumbnail() {
		return this.thumbnail != null;
	}
	
	@Override
	final public boolean equals(Object obj) {
		if(obj instanceof ImageMetaData) {
			ImageMetaData other = (ImageMetaData) obj;
			return Objects.equals(this.anchor, other.anchor) && Objects.equals(this.path, other.path);
		}
		
		return false;
	}
	
	@Override
	final public int hashCode() {
		return Objects.hash(this.anchor, this.path);
	}
}
