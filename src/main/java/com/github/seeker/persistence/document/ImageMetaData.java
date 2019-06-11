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
	 * The path of the file. Must be unique per anchor.
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
	
	//TODO index this or create another boolean field? Index size? performance?
	/**
	 * Thumbnail data for the image, if any
	 */
	private byte[] thumbnail;
	
	@Index
	private List<String> tags;
}
