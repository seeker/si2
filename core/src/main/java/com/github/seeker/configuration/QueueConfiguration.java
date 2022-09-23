package com.github.seeker.configuration;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
	private boolean integration;
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
		thumbnails
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
	 * @throws IOException if there is an error declaring queues
	 */
	public QueueConfiguration(Channel channel) throws IOException {
		this(channel, false);
	}
	
	/**
	 * Create a new Queue configuration.
	 * 
	 * If the integration parameter is set to true, the queues are created as auto-delete,
	 * and the queue names will be prefixed with `integration-` 
	 * 
	 * @param channel a channel to declare the queues on
	 * @param integration if the queues will be used for integration testing 
	 * @throws IOException if there is an error declaring queues
	 */
	public QueueConfiguration(Channel channel, boolean integration) throws IOException {
		this.channel = channel;
		this.integration = integration;
		
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
		LOGGER.info("Declaring {} queues...", ConfiguredQueues.values().length);
		
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
	 * Get the queue name for the configured queue. If the configuration is in
	 * integration test mode, the queue names will be prefixed with integration-
	 * 
	 * @param queue to get the name for
	 * @return the queue name
	 */
	public String getQueueName(ConfiguredQueues queue) {
		if (Objects.isNull(queue)) {
			LOGGER.error("Queue cannot be null");
			throw new IllegalStateException("Queue cannot be null");
		}

		if (integration) {
			return "integration-" + queue.toString();
		} else {
			return queue.toString();
		}
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

	private int getQueueCount() {
		return ConfiguredQueues.values().length;
	}

	/**
	 * Delete the queue. Will throw an exception if the queue does not exist.
	 * 
	 * @param queue to delete
	 * @throws IOException if there is an error
	 */
	public void deleteQueue(ConfiguredQueues queue) throws IOException {
		LOGGER.info("Deleting queue {}", queue);
		String queueName = getQueueName(queue);

		channel.queueDelete(queueName);
	}

	/**
	 * Delete all queues in {@link ConfiguredQueues}.
	 * 
	 * @throws IOException if there is an error
	 */
	public void deleteAllQueues() throws IOException {
		LOGGER.info("Deleting all {} queues...", getQueueCount());

		for (ConfiguredQueues queue : ConfiguredQueues.values()) {
			deleteQueue(queue);
		}
	}
}
