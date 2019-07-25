/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.processor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.io.ImageFileFilter;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.impl.AMQBasicProperties;

/**
 * Visits and loads files into the queue.
 */
public class FileToQueueVistor extends SimpleFileVisitor<Path> {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileToQueueVistor.class);

	private final ImageFileFilter fileFilter = new ImageFileFilter();
	private final Channel channel;
	private final String loadedFileQueue;
	private final String anchor;
	private final Path anchorPath;
	
	private final String metaDataQueue = "file-meta-data"; 
	
	public FileToQueueVistor(Channel channel, String anchor, Path anchorPath, String loadedFileQueue) {
		this.channel = channel;
		this.anchor = anchor;
		this.anchorPath = anchorPath;
		this.loadedFileQueue = loadedFileQueue;
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		if(fileFilter.accept(file)) {
			try {
				loadFileIntoQueue(file, attrs);
			} catch (Exception e) {
				LOGGER.warn("Failed to process {}: {}", file, e.getMessage());
				e.printStackTrace();
			}
		} else {
			LOGGER.trace("Skipping {}", file);
		}

		return super.visitFile(file, attrs);
	}
	
	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
		LOGGER.warn("Failed to visit {} due to {}",file, exc.getMessage());
		return super.visitFileFailed(file, exc);
	}
	
	private void loadFileIntoQueue(Path file, BasicFileAttributes attrs) throws IOException {
		byte[] rawImageData = Files.readAllBytes(file); 
		
		Path relativeToAnchor = anchorPath.relativize(file);
		
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put("anchor", anchor);
		headers.put("path", relativeToAnchor.toString());
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();
		
		channel.basicPublish("", loadedFileQueue, props, rawImageData);
	}
}
