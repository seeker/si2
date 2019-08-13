/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.messaging;

/**
 * Object that contains the decoded hash message.
 */
public class HashMessage {
	private String anchor;
	private String reltaivePath;
	private byte[] sha256;
	private long phash;
	
	public HashMessage(String anchor, String reltaivePath, byte[] sha256, long phash) {
		super();
		this.anchor = anchor;
		this.reltaivePath = reltaivePath;
		this.sha256 = sha256;
		this.phash = phash;
	}

	public String getAnchor() {
		return anchor;
	}

	public String getReltaivePath() {
		return reltaivePath;
	}

	public byte[] getSha256() {
		return sha256;
	}

	public long getPhash() {
		return phash;
	}
}
