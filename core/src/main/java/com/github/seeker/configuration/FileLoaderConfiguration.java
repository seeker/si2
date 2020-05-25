/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import java.util.Map;

public interface FileLoaderConfiguration {

	/**
	 * Load anchor / path pairs for the file loader. cfg4j's format is used. The
	 * paths must point to directories and the path format is OS dependent where the
	 * node is running.
	 * 
	 * Example:
	 * loader.anchors:
	 *   - "anchorOne=/var/test/data"
	 *	 - "anchorTwo=D:\\test\\path"
	 * 
	 * @return A map of anchors and paths
	 */
	Map<String, String> anchors();
}
