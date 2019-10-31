package com.github.seeker.app;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.messaging.HashMessageBuilder;
import com.github.seeker.messaging.HashMessageHelper;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Fetches images from the queue and generates hashes and the thumbnail.
 * The results are sent as a new message.
 */
public class FileProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileProcessor.class);

	private final Channel channel;
	private final QueueConfiguration queueConfig;
	
	public FileProcessor(Channel channel, ConsulClient consul, QueueConfiguration queueConfig) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", FileProcessor.class.getSimpleName());
		
		this.channel = channel;
		this.queueConfig = queueConfig;
		

		channel.basicQos(20);
		
		processFiles();
	}
	
	public FileProcessor(ConnectionProvider connectionProvider) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", FileProcessor.class.getSimpleName());
		
		ConsulClient consul = connectionProvider.getConsulClient();
		Connection conn = connectionProvider.getRabbitMQConnection();
		channel = conn.createChannel();
		
		queueConfig = new QueueConfiguration(channel, consul);
		
		channel.basicQos(20);
		
		processFiles();
	}

	public void processFiles() throws IOException, InterruptedException {
		String queueName =  queueConfig.getQueueName(ConfiguredQueues.fileDigest);
		LOGGER.info("Starting consumer on queue {}", queueName);
		channel.basicConsume(queueName, new FileMessageConsumer(channel, queueConfig));
	}
}

class FileMessageConsumer extends DefaultConsumer {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileMessageConsumer.class);

	private final HashMessageBuilder hashMessageBuilder;
	private final HashMessageHelper hashMessageHelper;
	
	public FileMessageConsumer(Channel channel, QueueConfiguration queueConfig) {
		super(channel);
		this.hashMessageBuilder = new HashMessageBuilder(channel, queueConfig);
		
		this.hashMessageHelper = new HashMessageHelper();
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		Map<String, Object> originalHeader = properties.getHeaders();
		
		
		String requiredHashes = originalHeader.get(MessageHeaderKeys.HASH_ALGORITHMS).toString();
		String[] hashes = requiredHashes.split(",");
		
		LOGGER.debug("File {}:{} hash request for algorithms: {}", hashMessageHelper.getAnchor(originalHeader), hashMessageHelper.getRelativePath(originalHeader), hashes);
		
		for (String hash : hashes) {
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				hashMessageBuilder.addHash(hash, md.digest(body));
			} catch (NoSuchAlgorithmException e) {
				// TODO send a error message back
				e.printStackTrace();
			}
		}
		
		hashMessageBuilder.send(originalHeader);
		LOGGER.debug("Consumed message for {} - {} > hashes: {}", originalHeader.get("anchor"), originalHeader.get("path"),	originalHeader.get("missing-hash"));
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}
}