package com.github.seeker.app;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.VaultException;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.proto.DbUpdateOuterClass.DbUpdate;
import com.github.seeker.messaging.proto.DbUpdateOuterClass.UpdateType;
import com.github.seeker.messaging.proto.ImagePathOuterClass.ImagePath;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.Hash;
import com.github.seeker.persistence.document.ImageMetaData;
import com.github.seeker.persistence.document.Thumbnail;
import com.google.protobuf.ByteString;
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

	private final MongoDbMapper mapper;
	private final QueueConfiguration queueConfig;
	
	public DBNode(ConnectionProvider connectionProvider) throws IOException, TimeoutException, InterruptedException, VaultException {
		this(connectionProvider.getConsulClient(), connectionProvider.getMongoDbMapper(), connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.dbnode).newConnection());
	}
	
	public DBNode(ConsulClient consul, MongoDbMapper mapper, Connection rabbitMqConnection) throws IOException, TimeoutException, InterruptedException {
		this(consul, mapper, rabbitMqConnection, new QueueConfiguration(rabbitMqConnection.createChannel()));
	}
	
	public DBNode(ConsulClient consul, MongoDbMapper mapper, Connection rabbitMqConnection, QueueConfiguration queueConfig) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", DBNode.class.getSimpleName());
		
		this.queueConfig = queueConfig;
		this.mapper = mapper;
		
		startConsumers(rabbitMqConnection);
	}

	private void startConsumers(Connection rabbitmqConnection) throws IOException {
		Channel dbStoreChannel = rabbitmqConnection.createChannel();
		dbStoreChannel.basicQos(100);
		String queueName = queueConfig.getQueueName(ConfiguredQueues.persistence);
		LOGGER.info("Starting consumer on queue {}", queueName);
		dbStoreChannel.basicConsume(queueName, new DBStore(dbStoreChannel, mapper));
	}
}

class DBStore extends DefaultConsumer {

	private static final Logger LOGGER = LoggerFactory.getLogger(DBStore.class);

	private final MongoDbMapper mapper;
	
	public DBStore(Channel channel, MongoDbMapper mapper) {
		super(channel);
		
		this.mapper = mapper;
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		DbUpdate message = DbUpdate.parseFrom(body);
		ImagePath imagePath = message.getImagePath();

		String anchor = imagePath.getAnchor();
		String relativeAnchorPath = imagePath.getRelativePath();
		
		ImageMetaData meta = mapper.getImageMetadata(anchor, relativeAnchorPath);
		
		if(meta == null) {
			LOGGER.warn("No metadata found in database for {} - {}", anchor, relativeAnchorPath);
			meta = new ImageMetaData();
			meta.setAnchor(anchor);
			meta.setPath(relativeAnchorPath.toString());
		}
		
		UpdateType type = message.getUpdateType();

		switch (type) {
		// FIXME Remove update type to avoid fall through
		case UPDATE_TYPE_CUSTOM_HASH:
		case UPDATE_TYPE_DIGEST_HASH:
			handleHashUpdate(message, meta);
			break;
		case UPDATE_TYPE_THUMBNAIL:
			handleThumbnailUpdate(message, meta);
			break;
		default:
			LOGGER.warn("Message with unhandled update type: {}", type);
			break;
		}

		mapper.storeDocument(meta);
		LOGGER.info("Updated {} database entry for {} - {} with ID {}", type, anchor, relativeAnchorPath, meta.getImageId());

		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}

	private void handleHashUpdate(DbUpdate message, ImageMetaData meta) {
		Map<String, ByteString> hashes = message.getDigestHashMap();

		for (Entry<String, ByteString> entry : hashes.entrySet()) {
			meta.getHashes().put(entry.getKey(), new Hash(entry.getKey(), entry.getValue().toByteArray(), "1"));
		}
	}

	private void handleThumbnailUpdate(DbUpdate message, ImageMetaData meta) {
		int imageSize = message.getThumbnailSize();

		Thumbnail thumbnail;

		if (meta.hasThumbnail()) {
			thumbnail = meta.getThumbnail();
			thumbnail.setMaxImageSize(imageSize);
		} else {
			thumbnail = new Thumbnail(imageSize);
			meta.setThumbnailId(thumbnail);
		}
	}
}
