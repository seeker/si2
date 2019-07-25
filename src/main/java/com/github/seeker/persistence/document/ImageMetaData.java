/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence.document;

import java.util.Date;
import java.util.List;
import java.util.Map;

import de.caluga.morphium.annotations.CreationTime;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.driver.bson.MorphiumId;

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
	private Map<String, String> hashes;
	
	/**
	 * Thumbnail for the image, if any
	 */
	private Thumbnail thumbnail;
	
	/**
	 * Tags assigned to this image
	 */
	@Index
	private List<String> tags;

	public Thumbnail getThumbnail() {
		return thumbnail;
	}

	public void setThumbnail(Thumbnail thumbnail) {
		this.thumbnail = thumbnail;
	}
}
