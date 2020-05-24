/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.messaging;

public class MessageHeaderKeys {
	public static final String ANCHOR = "anchor";
	public static final String ANCHOR_RELATIVE_PATH = "path";
	public static final String HASH_SHA256 = "sha256";
	public static final String HASH_PHASH = "phash";
	public static final String THUMBNAIL_FOUND = "thumbnail-found";
	/**
	 * Thumbnail size parameter that was used to generate the thumbnail.
	 */
	public static final String THUMBNAIL_SIZE = "tn-size";

	/**
	 * Hash algorithms that the body should be hashed with, or the resulting hashes are contained in the body.
	 */
	public static final String HASH_ALGORITHMS = "hash-algo";

	/**
	 * Custom hash algorithms that the body should be hashed with, or the resulting hashes are contained in the body.
	 */
	public static final String CUSTOM_HASH_ALGORITHMS = "cust-hash-algo";

	/**
	 * Command message for the fileloader.
	 */
	public static final String FILE_LOADER_COMMAND = "loader-command";
	
	private MessageHeaderKeys() {
	}
}
