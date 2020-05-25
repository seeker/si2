package com.github.seeker.configuration;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;

/**
 * Class that sets up queues.
 */
public class QueueConfiguration {
	private static final Logger LOGGER = LoggerFactory.getLogger(QueueConfiguration.class);
	
	private Channel channel;
	private ConsulClient consulClient;
	private boolean integration;
	private Map<ConfiguredQueues, String> queueNames;
	private Map<ConfiguredExchanges, String> exchangeNames;
	
	public enum ConfiguredQueues 
	{
		/**
		 * Files in this queue will be processed directly, using the raw data
		 */
		fileDigest,
		
		/**
		 * Files in this queue will be resized
		 */
		fileResize,
		
		
		/**
		 * Messages in this queue are pre-processed files. They need to be further processed to yield a usable hash.
		 */
		filePreProcessed,
		/**
		 * Queue for computed hashes
		 */
		hashes,
		/**
		 * Queue for generated thumbnails
		 */
		thumbnails,
		
		/**
		 * Queue for requesting / loading stored thumbnails
		 */
		thumbnailRequests
	};
	
	public enum ConfiguredExchanges {
		/**
		 * Exchange for sending commands to loader instances.
		 */
		loaderCommand,
		/**
		 * Exchange for loader to place loaded image data.
		 */
		loader
	};

	/**
	 * Create a new Queue configuration.
	 * 
	 * @param channel a channel to declare the queues on
	 * @param consulClient to get the queue names from consul
	 * @throws IOException if there is an error declaring queues
	 */
	public QueueConfiguration(Channel channel, ConsulClient consulClient) throws IOException {
		this(channel, consulClient, false);
	}
	
	/**
	 * Create a new Queue configuration.
	 * 
	 * If the integration parameter is set to true, the queues are created as auto-delete,
	 * and the queue names will be prefixed with `integration-` 
	 * 
	 * @param channel a channel to declare the queues on
	 * @param consulClient to get the queue names from consul
	 * @param integration if the queues will be used for integration testing 
	 * @throws IOException if there is an error declaring queues
	 */
	public QueueConfiguration(Channel channel, ConsulClient consulClient, boolean integration) throws IOException {
		this.channel = channel;
		this.consulClient = consulClient;
		this.integration = integration;
		
		setupQueueNames(setupConsulKeys());
		setupExchangeNames();
		declareExchanges();
		declareQueues();
	}
	
	/**
	 * Check if this configuration was set up for integration testing. 
	 * 
	 * @return true if set up for integration testing
	 */
	public boolean isIntegrationConfig() {
		return this.integration;
	}

	private Map<ConfiguredQueues, String> setupConsulKeys() {
		LOGGER.debug("Preparing consul keys for queue names");
		
		Map<ConfiguredQueues, String> keyToQueues = new HashMap<QueueConfiguration.ConfiguredQueues, String>();
		
		keyToQueues.put(ConfiguredQueues.hashes, "config/rabbitmq/queue/hash");
		keyToQueues.put(ConfiguredQueues.thumbnails, "config/rabbitmq/queue/thumbnail");
		keyToQueues.put(ConfiguredQueues.thumbnailRequests, "config/rabbitmq/queue/thumbnail-request");		
		keyToQueues.put(ConfiguredQueues.fileDigest, "config/rabbitmq/queue/file-digest");
		keyToQueues.put(ConfiguredQueues.fileResize, "config/rabbitmq/queue/file-resize");
		keyToQueues.put(ConfiguredQueues.filePreProcessed, "config/rabbitmq/queue/file-pre-processed");
		
		return keyToQueues;
	}

	private void setupQueueNames(Map<ConfiguredQueues, String> keyToQueues) {
		queueNames = new HashMap<QueueConfiguration.ConfiguredQueues, String>();
		
		LOGGER.debug("Fetching queue names for {} queues", ConfiguredQueues.values().length);
		
		for(ConfiguredQueues queue : ConfiguredQueues.values()) {
			String queueName = consulClient.getKvAsString(keyToQueues.get(queue));
			
			if(integration) {
				queueName = "integration-" + queueName + "-" + UUID.randomUUID().toString();
			}
			
			queueNames.put(queue, queueName);
		}
	}
	
	private void setupExchangeNames() {
		exchangeNames = new HashMap<QueueConfiguration.ConfiguredExchanges, String>();

		LOGGER.debug("Fetching exchange names for {} exchanges", ConfiguredExchanges.values().length);

		for (ConfiguredExchanges exchange : ConfiguredExchanges.values()) {
			String exchangeName = exchange.toString();

			if (integration) {
				exchangeName = "integration-" + exchangeName;
			}

			exchangeNames.put(exchange, exchangeName);
		}
	}
	
	private void declareExchanges() throws IOException {
		LOGGER.info("Declaring exchanges...");
		
		channel.exchangeDeclare(getExchangeName(ConfiguredExchanges.loader), BuiltinExchangeType.FANOUT);
		channel.exchangeDeclare(getExchangeName(ConfiguredExchanges.loaderCommand), BuiltinExchangeType.FANOUT);
	}
	
	private void declareQueues() throws IOException {
		LOGGER.info("Declaring {} queues...", queueNames.size());
		for(ConfiguredQueues queue : ConfiguredQueues.values()) {
			LOGGER.debug("Declaring queue {} ...", getQueueName(queue));

			Map<String, Object> queueProperties;

			if (integration) {
				queueProperties = new HashMap<String, Object>();
				queueProperties.put("x-expires", 60000);
			} else {
				queueProperties = Collections.emptyMap();
			}

			channel.queueDeclare(getQueueName(queue), false, false, integration, queueProperties);
		}
		
		channel.queueBind(getQueueName(ConfiguredQueues.fileDigest), getExchangeName(ConfiguredExchanges.loader), "");
		channel.queueBind(getQueueName(ConfiguredQueues.fileResize), getExchangeName(ConfiguredExchanges.loader), "");
	}
	
	/**
	 * Get the queue name for the configured queue.
	 * @param queue to get the name for
	 * @return the queue name
	 * @throws IllegalStateException if there is no name for the queue
	 */
	public String getQueueName(ConfiguredQueues queue) {
		String queueName = queueNames.get(queue);
		
		if(queueName == null) {
			LOGGER.error("No quque name found for {}", queue);
			throw new IllegalStateException("No queue name found for " + queue);
		}
		
		return queueNames.get(queue);
	}
	
	/**
	 * Get the exchange name for the configured exchange.
	 * 
	 * @param exchange to get the name for
	 * @return the exchange name
	 * @throws IllegalStateException if there is no name for the exchange
	 */
	public String getExchangeName(ConfiguredExchanges exchange) {
		String queueName = exchangeNames.get(exchange);

		if (queueName == null) {
			LOGGER.error("No exchange name found for {}", exchange);
			throw new IllegalStateException("No exchange name found for " + exchange);
		}

		return exchangeNames.get(exchange);
	}
}
