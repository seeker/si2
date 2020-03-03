/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

public class VaultIntegrationCredentials implements VaultCredentials {
	public static enum Approle {dbnode};
	
	private final String role; 
	
	public VaultIntegrationCredentials(Approle approle) {
		role = approle.toString();
	}
	
	@Override
	public String approleId() {
		return role;
	}

	@Override
	public String secretId() {
		return role;
	}
}
