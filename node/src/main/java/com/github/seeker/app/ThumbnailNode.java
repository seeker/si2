package com.github.seeker.app;

import java.io.DataInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;
import com.google.common.io.ByteStreams;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Loads files from the file system and sends them to the message broker with
 * additional meta data.
 */
public class ThumbnailNode {
	private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailNode.class);

	private String thumbnailDirectory = "thumbs";
	
	private final Channel channel;
	private final Channel channelThumbRequ;
	private final MongoDbMapper mapper;
	private final QueueConfiguration queueConfig;
	
	public ThumbnailNode(ConnectionProvider connectionProvider, String thumbnailDirectory) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", ThumbnailNode.class.getSimpleName());
		LOGGER.info("Using thumbnail directory {}", Paths.get(thumbnailDirectory).toAbsolutePath());
		
		this.thumbnailDirectory = thumbnailDirectory;
		
		ConsulClient consul = connectionProvider.getConsulClient();
		Connection conn = connectionProvider.getRabbitMQConnection();
		channel = conn.createChannel();
		channel.basicQos(100);
		
		channelThumbRequ = conn.createChannel();
		channelThumbRequ.basicQos(1);
		
		queueConfig = new QueueConfiguration(channel, consul);
		mapper = connectionProvider.getMongoDbMapper();
		
		startConsumers();
	}

	private void startConsumers() throws IOException {
		String queueName = queueConfig.getQueueName(ConfiguredQueues.thumbnails);
		String thumbnailRequestQueue = queueConfig.getQueueName(ConfiguredQueues.thumbnailRequests);
		
		LOGGER.info("Starting consumer on queue {}", queueName);
		channel.basicConsume(queueName, new ThumbnailStore(channel, mapper, thumbnailDirectory));
		
		LOGGER.info("Starting consumer on queue {}", thumbnailRequestQueue);
		channelThumbRequ.basicConsume(thumbnailRequestQueue, new ThumbnailLoad(channelThumbRequ,thumbnailDirectory));
	}
}

class ThumbnailLoad extends DefaultConsumer {

	private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailStore.class);

	private final Path baseThumbnailDirectory;
	
	public ThumbnailLoad(Channel channel, String baseThumbnailDirectory) {
		super(channel);
		
		this.baseThumbnailDirectory = Paths.get(baseThumbnailDirectory);
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		DataInput in  = ByteStreams.newDataInput(body);
		long most = in.readLong();
		long least = in.readLong();
		
		UUID uuid = new UUID(most, least);
		LOGGER.debug("Received thumbnail image request for UUID {}", uuid);
		
		Path thumbnailDirectory = generateDirectories(uuid);
		Path absoluteThumbnail = thumbnailDirectory.resolve(uuid.toString()).toAbsolutePath();
		
		if(! Files.exists(absoluteThumbnail)) {
			LOGGER.error("Thumbnail {} not found at {}", uuid, absoluteThumbnail);
			noThumbnailFound(properties);
		}else {
			byte[] imageData = loadThumbnail(absoluteThumbnail);
			responseMessage(properties, true, imageData);
			LOGGER.debug("Fulfilled request for thumbnail with UUID{}", uuid);
		}
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}
	
	private Path generateDirectories(UUID uuid) throws IOException {
		String imageName = uuid.toString();
		
		String firstCharacter = imageName.subSequence(0, 1).toString();
		String firstTwoCharacters = imageName.subSequence(0, 2).toString();
		
		Path thumbnailDirectory = baseThumbnailDirectory.resolve(firstCharacter).resolve(firstTwoCharacters);
		
		return thumbnailDirectory;
	}
	
	private byte[] loadThumbnail(Path absoluteThumbnail) throws IOException {
		return Files.readAllBytes(absoluteThumbnail);
	}
	
	private void noThumbnailFound(BasicProperties properties) throws IOException {
		responseMessage(properties, false, null);
	}
	
	private void responseMessage(BasicProperties properties, boolean thumbnailFound, byte[] thumbnailImageData) throws IOException {
		Map<String, Object> messageHeaders = new HashMap<String, Object>();
		messageHeaders.put(MessageHeaderKeys.THUMBNAIL_FOUND, Boolean.toString(thumbnailFound));
		
		AMQP.BasicProperties messageProps = new AMQP.BasicProperties.Builder().headers(messageHeaders).correlationId(properties.getCorrelationId()).build();
		getChannel().basicPublish("", properties.getReplyTo(), messageProps, thumbnailImageData);
	}
}

class ThumbnailStore extends DefaultConsumer {

	private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailStore.class);

	private final MongoDbMapper mapper;
	private final Path baseThumbnailDirectory;
	
	
	
	public ThumbnailStore(Channel channel, MongoDbMapper mapper, String baseThumbnailDirectory) {
		super(channel);
		
		this.mapper = mapper;
		this.baseThumbnailDirectory = Paths.get(baseThumbnailDirectory);
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		Map<String, Object> header = properties.getHeaders();
		
		String anchor = header.get("anchor").toString();
		Path relativeAnchorPath = null;
		try {
			relativeAnchorPath = Paths.get(header.get("path").toString());
		} catch (InvalidPathException e) {
			LOGGER.warn("Invalid path {}", e);
			return;
		}
		
		ImageMetaData meta = mapper.getImageMetadata(anchor, relativeAnchorPath);
		
		UUID uuid;
		
		if(meta.hasThumbnail()) {
			uuid = meta.getThumbnailId();
		} else {
			uuid = UUID.randomUUID();
			meta.setThumbnailId(uuid);
		}
		
		Path thumbnailDirectory = generateDirectories(uuid);
		storeThumbnail(thumbnailDirectory, uuid, body);

		mapper.storeDocument(meta);
		LOGGER.info("Stored thumbnail for {} - {} in {}", anchor, relativeAnchorPath, thumbnailDirectory.resolve(uuid.toString()));
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}
	
	private Path generateDirectories(UUID uuid) throws IOException {
		String imageName = uuid.toString();
		
		String firstCharacter = imageName.subSequence(0, 1).toString();
		String firstTwoCharacters = imageName.subSequence(0, 2).toString();
		
		Path thumbnailDirectory = baseThumbnailDirectory.resolve(firstCharacter).resolve(firstTwoCharacters);
		LOGGER.debug("Creating directory {}", thumbnailDirectory);
		
		Files.createDirectories(thumbnailDirectory);
		
		return thumbnailDirectory;
	}
	
	private void storeThumbnail(Path thumbnailDirectory, UUID thumbnailID, byte[] imageData) throws IOException {
		Files.write(thumbnailDirectory.resolve(thumbnailID.toString()), imageData);
	}
}