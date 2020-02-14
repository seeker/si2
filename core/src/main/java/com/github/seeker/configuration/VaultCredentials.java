/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

public interface VaultCredentials {
	/**
	 * The Vault approle role ID for this instance.
	 * @return the approle role ID as a string 
	 */
	String approleId();
	/**
	 * The approle secret ID that matches the used approle role ID.
	 * @return
	 */
	String secretId();
}
