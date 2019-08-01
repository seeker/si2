package com.github.seeker.app;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.messaging.HashMessage;
import com.github.seeker.messaging.HashMessageHelper;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.Hash;
import com.github.seeker.persistence.document.ImageMetaData;
import com.google.common.primitives.Longs;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Loads files from the file system and sends them to the message broker with
 * additional meta data.
 */
public class DBNode {
	private static final Logger LOGGER = LoggerFactory.getLogger(DBNode.class);

	private final Channel channel;
	private final MongoDbMapper mapper;
	private final QueueConfiguration queueConfig;
	private final HashMessageHelper hashMessageHelper;
	private final List<String> requiredHashes;
	
	public DBNode(ConnectionProvider connectionProvider) throws IOException, TimeoutException, InterruptedException {
		this(connectionProvider.getConsulClient(), connectionProvider.getMongoDbMapper(), connectionProvider.getRabbitMQConnection());
	}
	
	public DBNode(ConsulClient consul, MongoDbMapper mapper, Connection rabbitMqConnection) throws IOException, TimeoutException, InterruptedException {
		this(consul, mapper, rabbitMqConnection, new QueueConfiguration(rabbitMqConnection.createChannel(), consul));
	}
	
	public DBNode(ConsulClient consul, MongoDbMapper mapper, Connection rabbitMqConnection, QueueConfiguration queueConfig) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", DBNode.class.getSimpleName());
		
		Connection conn = rabbitMqConnection;
		channel = conn.createChannel();
		channel.basicQos(100);
		
		this.queueConfig = queueConfig;
		this.mapper = mapper;
		
		requiredHashes = Arrays.asList(consul.getKvAsString("config/general/required-hashes").split(Pattern.quote(",")));
		hashMessageHelper = new HashMessageHelper(channel, queueConfig);
		
		startConsumers();
	}

	private void startConsumers() throws IOException {
		String queueName = queueConfig.getQueueName(ConfiguredQueues.hashes);
		LOGGER.info("Starting consumer on queue {}", queueName);
		channel.basicConsume(queueName, new DBStore(channel, mapper, hashMessageHelper, requiredHashes));
	}
}

class DBStore extends DefaultConsumer {

	private static final Logger LOGGER = LoggerFactory.getLogger(DBStore.class);

	private final MongoDbMapper mapper;
	private final List<String> requiredHashes;
	private final HashMessageHelper hashMessageHelper;
	
	public DBStore(Channel channel, MongoDbMapper mapper,HashMessageHelper hashMessageHelper ,List<String> requiredHashes) {
		super(channel);
		
		this.mapper = mapper;
		this.requiredHashes = requiredHashes;
		this.hashMessageHelper = hashMessageHelper;
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		HashMessage message = hashMessageHelper.decodeHashMessage(properties.getHeaders(), body);
		
		String anchor = message.getAnchor();
		String relativeAnchorPath = message.getReltaivePath();
		
		ImageMetaData meta = mapper.getImageMetadata(message.getAnchor(), message.getReltaivePath());
		
		if(meta == null) {
			LOGGER.warn("No metadata found in database for {} - {}", anchor, relativeAnchorPath);
			meta = new ImageMetaData();
			meta.setAnchor(anchor);
			meta.setPath(relativeAnchorPath.toString());
		}
		
		updateMetadata(message, meta);

		mapper.storeDocument(meta);
		LOGGER.info("Updated database entry for {} - {}", anchor, relativeAnchorPath);
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}
	
	private void updateMetadata(HashMessage message, ImageMetaData meta) {
		//TODO use required hashes
		
		Map<String, Hash> metaHashes = meta.getHashes();
		metaHashes.put("sha256", new Hash(message.getSha256()));
		metaHashes.put("phash", new Hash(Longs.toByteArray(message.getPhash())));

		mapper.storeDocument(meta);
	}
}