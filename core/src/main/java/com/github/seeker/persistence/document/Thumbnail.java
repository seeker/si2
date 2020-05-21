package com.github.seeker.persistence.document;

import java.util.UUID;

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
	public Thumbnail(int maxImageSize, UUID imageId) {
		this.maxImageSize = maxImageSize;
		this.imageId = imageId;
	}

	/**
	 * The size in pixels, of the longest side of the thumbnail.
	 */
	private int maxImageSize;
	
	/**
	 * The image ID as a UUID.
	 */
	private UUID imageId;

	public int getMaxImageSize() {
		return maxImageSize;
	}

	public void setMaxImageSize(int maxImageSize) {
		this.maxImageSize = maxImageSize;
	}

	public UUID getImageId() {
		return imageId;
	}
}
