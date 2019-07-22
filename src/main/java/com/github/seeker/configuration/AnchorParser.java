/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the string encoded anchors and paths.
 */
public class AnchorParser {
	private static final Logger LOGGER = LoggerFactory.getLogger(AnchorParser.class);
	
	public static final String DEFAULT_KV_SEPARATOR = "=";
	public static final String DEFAULT_PAIR_DELIMITER = ";";
	
	/**
	 * Separates a key/value pair, e.g. foo=/mnt/baz
	 */
	private final String kvSeparator;
	
	/**
	 * Separates two key/value pairs, e.g. foo=/mnt/baz;bar=/mnt/boo
	 */
	private final String pairDelimiter;
	
	public AnchorParser(String kvSeparator, String pairDelimiter) {
		this.kvSeparator = kvSeparator;
		this.pairDelimiter = pairDelimiter;
	}
	
	public AnchorParser() {
		this(DEFAULT_KV_SEPARATOR, DEFAULT_PAIR_DELIMITER);
	}
	
	public Map<String, String> parse(String encodedAnchors) {
		if(encodedAnchors == null) {
			throw new IllegalArgumentException("Encoded string cannot be null");
		}
		
		String encodedKvPairs[] = encodedAnchors.split(Pattern.quote(pairDelimiter));
		
		Map<String, String> anchors = new HashMap<String, String>();

		for(String encodedPair : encodedKvPairs) {
			String pair[] = encodedPair.split(Pattern.quote(kvSeparator));
			
			if(pair.length != 2) {
				LOGGER.warn("'{}' did not yield a key / value pair", encodedPair);
				continue;
			}
			
			anchors.put(pair[0], pair[1]);
		}
		
		return anchors;
	}
}
