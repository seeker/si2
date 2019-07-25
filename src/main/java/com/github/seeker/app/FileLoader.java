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
import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.processor.FileToQueueVistor;
import com.orbitz.consul.model.health.ServiceHealth;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;

/**
 * Loads files from the file system and sends them to the message broker with additional meta data.
 */
public class FileLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileLoader.class);
	
	private final Channel channel;
	private final MongoDbMapper mapper;
	private final List<String> requriedHashes;
	private final String queueFileFeed;
	
	//TODO use JSON and parser lib (retrofit?) to get data from consul, load data with curl?
	//TODO get file types from consul
	
	public FileLoader(String id) throws IOException, TimeoutException {
		ConsulClient consul = new ConsulClient(new ConfigurationBuilder().getConsulConfiguration());
		ServiceHealth rabbitmqService = consul.getFirstHealtyInstance(ConfiguredService.rabbitmq);
		
		String serverAddress = rabbitmqService.getNode().getAddress();
		int serverPort = rabbitmqService.getService().getPort();
		
		
		ConnectionFactory connFactory = new ConnectionFactory();
		connFactory.setUsername("si2");
		connFactory.setPassword(consul.getKvAsString("config/rabbitmq/users/si2"));
		connFactory.setHost(serverAddress);
		connFactory.setPort(serverPort);
		
		queueFileFeed = consul.getKvAsString("config/rabbitmq/queue/loader-file-feed");
		
		LOGGER.info("Connecting to Rabbitmq server {}:{}", serverAddress, serverPort);
		
		Connection conn = connFactory.newConnection();
		channel = conn.createChannel();
		
		LOGGER.info("Creating queue {}", queueFileFeed);
		channel.queueDeclare(queueFileFeed, false, false, false, null);
		
		
		LOGGER.info("Loading anchors for ID {}...", id);
		String encodedAnchors = consul.getKvAsString("config/fileloader/anchors/" + id);
		LOGGER.debug("Loaded encoded anchors {} for ID {}", encodedAnchors, id);
		
		ServiceHealth mongodbService = consul.getFirstHealtyInstance(ConfiguredService.mongodb);
		
		String database = consul.getKvAsString("config/mongodb/database/si2");
		String mongoDBserverAddress = mongodbService.getNode().getAddress();
		
		MorphiumConfig cfg = new MorphiumConfig();
		LOGGER.info("Conneting to mongodb database {}", database);
		cfg.setDatabase(database);
		cfg.addHostToSeed(mongoDBserverAddress);
				
		Morphium morphium = new Morphium(cfg);
		mapper = new MongoDbMapper(morphium);
		
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
			Files.walkFileTree(anchorAbsolutePath, new FileToQueueVistor(channel,anchor,anchorAbsolutePath, mapper, requriedHashes, queueFileFeed));
		} catch (IOException e) {
			LOGGER.warn("Failed to walk file tree for {}: {}", anchorAbsolutePath, e.getMessage());
		}
	}
}
