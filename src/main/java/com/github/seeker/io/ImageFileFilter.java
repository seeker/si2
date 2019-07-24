/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.io;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import com.github.dozedoff.commonj.filefilter.FileExtensionFilter;

/**
 * Filter class for filtering images based on the file extension;
 */
public class ImageFileFilter implements Filter<Path> {
	private static final String[] SUPPORTED_IMAGE_EXTENSIONS = { "jpg", "jpeg", "png", "gif" };
	private FileExtensionFilter extFilter;

	/**
	 * Create a filter with the following extensions: {@link #SUPPORTED_IMAGE_EXTENSIONS}.
	 * 
	 */
	public ImageFileFilter() {
		this.extFilter = new FileExtensionFilter(SUPPORTED_IMAGE_EXTENSIONS);
	}

	/**
	 * Accept the path if it ends with one of the supported file extensions.
	 * 
	 * @param entry
	 *            path to check
	 * @return true if the file is a supported image
	 * @throws IOException
	 *             if there is an error accessing the file
	 */
	@Override
	public boolean accept(Path entry) throws IOException {
		return extFilter.accept(entry);
	}
}
