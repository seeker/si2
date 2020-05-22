package com.github.seeker.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.VaultException;
import com.github.seeker.configuration.AnchorParser;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.processor.FileToQueueVistor;
import com.google.common.util.concurrent.RateLimiter;
import com.orbitz.consul.cache.KVCache;
import com.orbitz.consul.model.kv.Value;
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
	private final RateLimiter fileLoadRateLimiter;
	private final KVCache rateLimitCache;
	
	//TODO get file types from consul
	
	public FileLoader(String id, ConnectionProvider connectionProvider) throws IOException, TimeoutException, VaultException {
		ConsulClient consul = connectionProvider.getConsulClient();
		Connection conn = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.file_loader).newConnection();
		channel = conn.createChannel();
		queueConfig = new QueueConfiguration(channel, consul);
		
		long rateLimit = consul.getKvAsLong("config/fileloader/load-rate-limit");
		fileLoadRateLimiter = RateLimiter.create(rateLimit, 5, TimeUnit.SECONDS);
		LOGGER.info("Rate limiting messages to {}/s", rateLimit);
		
		rateLimitCache = consul.getKVCache("config/fileloader/load-rate-limit");
		rateLimitCache.addListener(newValues -> {
				Optional<Value> newValue = newValues.values().stream().filter(value -> value.getKey().equals("config/fileloader/load-rate-limit")).findAny();
				
				newValue.ifPresent(value -> {
					Optional<String> decodedLoadRateLimit = newValue.get().getValueAsString();
					decodedLoadRateLimit.ifPresent(rate -> {
						fileLoadRateLimiter.setRate(Double.parseDouble(rate));
						LOGGER.info("Ratelimit updated to {}", rate);
					});
					
				});
		});
		
		rateLimitCache.start();
		
		mapper = connectionProvider.getMongoDbMapper();
		
		requriedHashes = Arrays.asList(consul.getKvAsString("config/general/required-hashes").split(Pattern.quote(",")));
		
		loadFiles(encodedAnchors);
	}
	
	public void loadFiles(String encodedAnchors) {
		Map<String, String> anchors = new AnchorParser().parse(encodedAnchors);
		LOGGER.info("Loaded {} anchors", anchors.size());
		
		for (Entry<String, String> entry : anchors.entrySet()) {
			Path anchorAbsolutePath = Paths.get(entry.getValue());
			
			loadFilesForAnchor(entry.getKey(), anchorAbsolutePath);
		}

		rateLimitCache.stop();
		LOGGER.info("Finished walking anchors, terminating...");
	}
	
	private void loadFilesForAnchor(String anchor, Path anchorAbsolutePath) {
		LOGGER.info("Walking {} for anchor {}", anchorAbsolutePath, anchor);
		
		try {
			Files.walkFileTree(anchorAbsolutePath, new FileToQueueVistor(channel, fileLoadRateLimiter,anchor,anchorAbsolutePath, mapper, requriedHashes, queueConfig.getFileLoaderExchangeName()));
		} catch (IOException e) {
			LOGGER.warn("Failed to walk file tree for {}: {}", anchorAbsolutePath, e.getMessage());
		}
	}
}
