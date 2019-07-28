package com.github.seeker.persistence.document;

import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.driver.bson.MorphiumId;

/**
 * Simple container to store hash and related information.
 * This is to work around an issue that byte[] cannot be stored in a Map.
 */
@Entity
public class Hash {
	private static final String DEFAULT_VERSION = "1";
	
	@Id
	private MorphiumId id;
	private String version;
	@Index
	private byte[] hash;
	
	public Hash(byte[] hash) {
		this.hash = hash;
		this.version = DEFAULT_VERSION;
	}

	public Hash(String hashName, byte[] hash, String version) {
		this.hash = hash;
		this.version = version;
	}
	
	public String getVersion() {
		return version;
	}
	
	public void setVersion(String version) {
		this.version = version;
	}
	
	public byte[] getHash() {
		return hash;
	}
	
	public void setHash(byte[] hash) {
		this.hash = hash;
	}
}
