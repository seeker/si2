/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

/**
 * Container for mongodb configuration information.
 */
public interface MongoDbConfiguration {
	/**
	 * Get the ip address of the mongodb server.
	 * 
	 * @return the configured ip
	 */
	String ip();
	
	/**
	 * Return the database name to use for storing data.
	 * 
	 * @return the name of the database
	 */
	String database();
}
