package com.github.seeker.persistence.document;

import de.caluga.morphium.annotations.Embedded;

/**
 * Stores an image thumbnail and related metadata.
 */
@Embedded
public class Thumbnail {
	/**
	 * Create a new thumbnail document.
	 * 
	 * @param maxImageSize the size in pixels, of the longest side of the thumbnail
	 * @param imageId The UUID of the image
	 */
	public Thumbnail(int maxImageSize) {
		this.maxImageSize = maxImageSize;
	}

	/**
	 * The size in pixels, of the longest side of the thumbnail.
	 */
	private int maxImageSize;

	public int getMaxImageSize() {
		return maxImageSize;
	}

	public void setMaxImageSize(int maxImageSize) {
		this.maxImageSize = maxImageSize;
	}
}
