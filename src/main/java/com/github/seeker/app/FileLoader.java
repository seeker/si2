package com.github.seeker.app;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.AnchorParser;
import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.orbitz.consul.model.health.ServiceHealth;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Loads files from the file system and sends them to the message broker with additional meta data.
 */
public class FileLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileLoader.class);
	
	private final Channel channel;
	private final String queueFileFeed;
	
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
		loadFiles(encodedAnchors);
	}
	
	public void loadFiles(String encodedAnchors) {
		Map<String, String> anchors = new AnchorParser().parse(encodedAnchors);
		LOGGER.info("Loaded {} anchors}", anchors.size());
		
	}
}
