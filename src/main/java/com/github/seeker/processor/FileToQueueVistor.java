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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.io.ImageFileFilter;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.Hash;
import com.github.seeker.persistence.document.ImageMetaData;
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
	private final MongoDbMapper mapper;
	private final List<String> requiredHashes;
	private final String loadedFileQueue;
	private final String anchor;
	private final Path anchorPath;
	
	public FileToQueueVistor(Channel channel, String anchor, Path anchorPath, MongoDbMapper mapper, List<String> requiredHashes, String loadedFileQueue) {
		this.channel = channel;
		this.mapper = mapper;
		this.anchor = anchor;
		this.anchorPath = anchorPath;
		this.requiredHashes = requiredHashes;
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

		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
		LOGGER.warn("Failed to visit {} due to {}",file, exc.getMessage());
		return super.visitFileFailed(file, exc);
	}
	
	private void loadFileIntoQueue(Path file, BasicFileAttributes attrs) throws IOException {
		Path relativeToAnchor = anchorPath.relativize(file);
		
		LOGGER.trace("Fetching meta data for {} {}", anchor, relativeToAnchor);
		ImageMetaData meta = mapper.getImageMetadata(anchor, relativeToAnchor);
		
		if(meta == null) {
			meta = new ImageMetaData();
			
			meta.setAnchor(anchor);
			meta.setPath(relativeToAnchor.toString());
			meta.setFileSize(attrs.size());
			meta.setHashes(new HashMap<String, Hash>());
			mapper.storeDocument(meta);
		}
		
		List<String> missingHashes = new ArrayList<String>();
		
		for(String hash : requiredHashes) {
			if(!meta.getHashes().containsKey(hash)) {
				missingHashes.add(hash);
			}
		}

		if(missingHashes.isEmpty() && meta.hasThumbnail()) {
			return;
		}
		
		byte[] rawImageData = Files.readAllBytes(file); 
		
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put("missing-hash", String.join(",", missingHashes));
		headers.put("anchor", anchor);
		headers.put("path", relativeToAnchor.toString());
		headers.put("thumb", Boolean.toString(meta.hasThumbnail()));
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();
		
		channel.basicPublish("", loadedFileQueue, props, rawImageData);
	}
}
