package com.github.seeker.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.AnchorParser;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.processor.FileToQueueVistor;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

/**
 * Loads files from the file system and sends them to the message broker with additional meta data.
 */
public class FileLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileLoader.class);
	
	private final Channel channel;
	private final MongoDbMapper mapper;
	private final List<String> requriedHashes;
	private final QueueConfiguration queueConfig;
	
	//TODO use JSON and parser lib (retrofit?) to get data from consul, load data with curl?
	//TODO get file types from consul
	
	public FileLoader(String id, ConnectionProvider connectionProvider) throws IOException, TimeoutException {
		ConsulClient consul = connectionProvider.getConsulClient();
		Connection conn = connectionProvider.getRabbitMQConnection();
		channel = conn.createChannel();
		queueConfig = new QueueConfiguration(channel, consul);
		
		LOGGER.info("Loading anchors for ID {}...", id);
		String encodedAnchors = consul.getKvAsString("config/fileloader/anchors/" + id);
		LOGGER.debug("Loaded encoded anchors {} for ID {}", encodedAnchors, id);
						
		mapper = connectionProvider.getMongoDbMapper();
		
		requriedHashes = Arrays.asList(consul.getKvAsString("config/general/required-hashes").split(Pattern.quote(",")));
		
		loadFiles(encodedAnchors);
	}
	
	public void loadFiles(String encodedAnchors) {
		Map<String, String> anchors = new AnchorParser().parse(encodedAnchors);
		LOGGER.info("Loaded {} anchors}", anchors.size());
		
		for (Entry<String, String> entry : anchors.entrySet()) {
			Path anchorAbsolutePath = Paths.get(entry.getValue());
			
			loadFilesForAnchor(entry.getKey(), anchorAbsolutePath);
		}
	}
	
	private void loadFilesForAnchor(String anchor, Path anchorAbsolutePath) {
		LOGGER.info("Walking {} for anchor {}", anchorAbsolutePath, anchor);
		
		try {
			Files.walkFileTree(anchorAbsolutePath, new FileToQueueVistor(channel,anchor,anchorAbsolutePath, mapper, requriedHashes, queueConfig.getQueueName(ConfiguredQueues.files)));
		} catch (IOException e) {
			LOGGER.warn("Failed to walk file tree for {}: {}", anchorAbsolutePath, e.getMessage());
		}
	}
}
