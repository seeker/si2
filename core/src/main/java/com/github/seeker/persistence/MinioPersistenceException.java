/* The MIT License (MIT)
 * Copyright (c) 2022 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

/**
 * Class for wrapping the myriad of checked exceptions that the Minio client
 * throws.
 */
public class MinioPersistenceException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5009162497695079152L;

	public MinioPersistenceException(String message) {
		super(message);
	}

	public MinioPersistenceException(Throwable cause) {
		super(cause);
	}

	public MinioPersistenceException(String message, Throwable cause) {
		super(message, cause);
	}
}
