/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.messaging;

public class MessageHeaderKeys {
	/**
	 * Recreate only the thumbnail, regardless if it exists or not
	 */
	public static final String THUMBNAIL_RECREATE = "thumbnail-recreate";

	/**
	 * Command message for the fileloader.
	 */
	public static final String FILE_LOADER_COMMAND = "loader-command";
	
	/**
	 * Command message for the thumbnail node.
	 */
	public static final String THUMB_NODE_COMMAND = "thumb-command";
	
	private MessageHeaderKeys() {
	}
}
