/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.persistence.document.ImageMetaData;

import de.caluga.morphium.Morphium;

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
}
