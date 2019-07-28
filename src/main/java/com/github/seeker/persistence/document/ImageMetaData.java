/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence.document;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
	private UUID thumbnailId;
	
	/**
	 * Tags assigned to this image
	 */
	@Index
	private List<String> tags;

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

	public UUID getThumbnailId() {
		return thumbnailId;
	}

	public void setThumbnailId(UUID thumbnailId) {
		this.thumbnailId = thumbnailId;
	}
	
	/**
	 * Is there a thumbnail available for this image?
	 * @return true if there is a thumbnail
	 */
	public boolean hasThumbnail() {
		return this.thumbnailId != null;
	}
}
