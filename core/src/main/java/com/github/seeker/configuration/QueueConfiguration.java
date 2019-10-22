package com.github.seeker.configuration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	
	public enum ConfiguredQueues 
	{
		/**
		 * Queue containing files to process
		 */
		files,
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
		keyToQueues.put(ConfiguredQueues.files, "config/rabbitmq/queue/loader-file-feed");
		
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
	
	private void declareQueues() throws IOException {
		LOGGER.info("Declaring {} queues...", queueNames.size());
		
		for(ConfiguredQueues queue : ConfiguredQueues.values()) {
			LOGGER.debug("Declaring queue {} ...", getQueueName(queue));
			channel.queueDeclare(getQueueName(queue), false, false, integration, null);
		}
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
}
