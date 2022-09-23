package com.github.seeker.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.VaultException;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;
import com.github.seeker.persistence.document.Thumbnail;
import com.rabbitmq.client.AMQP.BasicProperties;
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
	
	public enum Command {
		prune_thumbnails
	}

	public ThumbnailNode(ConnectionProvider connectionProvider, String thumbnailDirectory) throws IOException, TimeoutException, InterruptedException, VaultException {
		LOGGER.info("{} starting up...", ThumbnailNode.class.getSimpleName());
		LOGGER.info("Using thumbnail directory {}", Paths.get(thumbnailDirectory).toAbsolutePath());
		
		this.thumbnailDirectory = thumbnailDirectory;
		
		ConsulClient consul = connectionProvider.getConsulClient();
		Connection conn = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.thumbnail).newConnection();
		channel = conn.createChannel();
		channel.basicQos(100);
		
		channelThumbRequ = conn.createChannel();
		channelThumbRequ.basicQos(1);
		
		queueConfig = new QueueConfiguration(channel);
		mapper = connectionProvider.getMongoDbMapper();
		
		startConsumers();
	}

	private void startConsumers() throws IOException {
		String queueName = queueConfig.getQueueName(ConfiguredQueues.thumbnails);
		
		LOGGER.info("Starting consumer on queue {}", queueName);
		channel.basicConsume(queueName, new ThumbnailStore(channel, mapper, thumbnailDirectory));
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
			getChannel().basicReject(envelope.getDeliveryTag(), false);
			return;
		}
		
		ImageMetaData meta = mapper.getImageMetadata(anchor, relativeAnchorPath);
		int imageSize = Integer.parseInt(header.get(MessageHeaderKeys.THUMBNAIL_SIZE).toString());
		
		Thumbnail thumbnail;
		
		if(meta.hasThumbnail()) {
			thumbnail = meta.getThumbnail();
			thumbnail.setMaxImageSize(imageSize);
		} else {
			thumbnail = new Thumbnail(imageSize);
			meta.setThumbnailId(thumbnail);
		}
		
		LOGGER.info("Updated thumbnail information for {} - {} with imageId {}", anchor, relativeAnchorPath, meta.getImageId());
		mapper.storeDocument(meta);

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
