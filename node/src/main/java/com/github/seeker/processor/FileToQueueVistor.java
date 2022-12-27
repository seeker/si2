/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.processor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.io.ImageFileFilter;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.messaging.UUIDUtils;
import com.github.seeker.persistence.MinioPersistenceException;
import com.github.seeker.persistence.MinioStore;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.Hash;
import com.github.seeker.persistence.document.ImageMetaData;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

/**
 * Visits and loads files into the queue.
 */
public class FileToQueueVistor extends SimpleFileVisitor<Path> {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileToQueueVistor.class);

	private static final String PHASH_CUSTOM_HASH_ALGORITHM_NAME = "phash";
	
	private final ImageFileFilter fileFilter = new ImageFileFilter();
	private final Channel channel;
	private final MongoDbMapper mapper;
	private final MinioStore minio;
	private final List<String> requiredHashes;
	private final List<String> requiredCustomHashes;
	private final String fileLoadExchange;
	private final String anchor;
	private final Path anchorRootPath;
	private boolean terminate = false;
	private boolean generateThumbnails = true;
	
	public FileToQueueVistor(Channel channel, String anchor, Path anchorRootPath, MongoDbMapper mapper,
			MinioStore minio, List<String> requiredHashes, String fileLoadExchange) {
		this.channel = channel;
		this.mapper = mapper;
		this.minio = minio;
		this.anchor = anchor;
		this.anchorRootPath = anchorRootPath;
		this.requiredHashes = requiredHashes;
		this.fileLoadExchange = fileLoadExchange;
		
		//TODO get required custom hashes from Consul
		requiredCustomHashes = new ArrayList<String>();
		requiredCustomHashes.add(PHASH_CUSTOM_HASH_ALGORITHM_NAME);
	}
	
	/**
	 * Gracefully terminate this {@link FileToQueueVistor}.
	 */
	public void terminate() {
		this.terminate = true;
	}
	
	/**
	 * Check if the termination flag has been set. This does not mean that the FileVisitor has terminated. 
	 * @return true if the terminate flag has been set
	 */
	public boolean isTerminated() {
		return terminate;
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		if (this.terminate) {
			LOGGER.info("Terminate flag set, terminating file walk...");
			return FileVisitResult.TERMINATE;
		}
		
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
		Path relativeToAnchor = anchorRootPath.relativize(file);
		
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
		List<String> missingCustomHashes = new ArrayList<String>();
		
		
		
		for(String hash : requiredHashes) {
			if(! meta.getHashes().containsKey(hash)) {
				missingHashes.add(hash);
			}
		}
		
		for(String hash :requiredCustomHashes) {
			if(! meta.getHashes().containsKey(hash)) {
				missingCustomHashes.add(hash);
			}
		}
		
		if(missingHashes.isEmpty() && missingCustomHashes.isEmpty() && meta.hasThumbnail()) {
			LOGGER.debug("Nothing to do for {}:{}, skipping message", anchor, relativeToAnchor);
			return;
		}
		
		try {
			minio.storeImage(file, meta.getImageId());

			Map<String, Object> headers = new HashMap<String, Object>();
			headers.put(MessageHeaderKeys.HASH_ALGORITHMS, String.join(",", missingHashes));
			headers.put(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS, String.join(",", missingCustomHashes));
			headers.put(MessageHeaderKeys.ANCHOR, anchor);
			headers.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, relativeToAnchor.toString());
			headers.put(MessageHeaderKeys.THUMBNAIL_FOUND, Boolean.toString(Boolean.logicalOr(!generateThumbnails, meta.hasThumbnail())));
			AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();

			channel.basicPublish(fileLoadExchange, "", props, UUIDUtils.UUIDtoByte(meta.getImageId()));
		} catch (IllegalArgumentException | IOException | MinioPersistenceException e) {
			LOGGER.error("Failed to upload image {} due to error {}", file, e.getMessage());
		}
	}

	/**
	 * Should thumbnails be generated for found images?
	 * 
	 * @return true if thumbnails should be generated
	 */
	public boolean isGenerateThumbnails() {
		return generateThumbnails;
	}

	/**
	 * Set if thumbnails should be generated for found images
	 * 
	 * @param generateThumbnails if set to true, thumbnails will be generated
	 */
	public void setGenerateThumbnails(boolean generateThumbnails) {
		this.generateThumbnails = generateThumbnails;
	}
}
