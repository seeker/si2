/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import com.github.seeker.configuration.MongoDbConfiguration;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;

/**
 * Create a Morphium client to interact with the database.
 */
public class MongoDbBuilder {
	
	/**
	 * Build a Morphium client.
	 * 
	 * @param mongoConfig to use for client configuration
	 * @return a configured {@link Morphium} instance
	 */
	public Morphium build(MongoDbConfiguration mongoConfig) {
		MorphiumConfig cfg = new MorphiumConfig();
		cfg.setDatabase(mongoConfig.database());
		cfg.addHostToSeed(mongoConfig.ip());
		return new Morphium(cfg);
	}
}
