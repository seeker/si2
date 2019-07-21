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
	 * @param imageData The image in encoded (JPG, PNG, etc.) binary form
	 */
	public Thumbnail(int maxImageSize, byte[] imageData) {
		this.maxImageSize = maxImageSize;
		this.imageData = imageData;
	}

	/**
	 * The size in pixels, of the longest side of the thumbnail
	 */
	private int maxImageSize;
	
	/**
	 * The image in encoded (JPG, PNG, etc.) binary form.
	 */
	private byte[] imageData;

	public int getMaxImageSize() {
		return maxImageSize;
	}

	public byte[] getImageData() {
		return imageData;
	}
}
