package com.github.seeker.app;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

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
import com.github.seeker.messaging.proto.FileLoadOuterClass.FileLoad;
import com.github.seeker.messaging.proto.ImagePathOuterClass.ImagePath;
import com.github.seeker.persistence.MinioPersistenceException;
import com.github.seeker.persistence.MinioStore;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Fetches images from the queue and generates hashes based on the requested Algorithms using {@link MessageDigest}.
 * The results are sent as a new message.
 */
public class MessageDigestHasher {
	private static final Logger LOGGER = LoggerFactory.getLogger(MessageDigestHasher.class);

	private final Connection rabbitMqConnection;
	private final MinioStore minio;
	private final QueueConfiguration queueConfig;
	
	public MessageDigestHasher(Connection rabbitMqConnection, ConsulClient consul, MinioStore minio,
			QueueConfiguration queueConfig)
			throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", MessageDigestHasher.class.getSimpleName());
		
		this.rabbitMqConnection = rabbitMqConnection;
		this.minio = minio;
		this.queueConfig = queueConfig;
		
		processFiles();
	}
	
	public MessageDigestHasher(ConnectionProvider connectionProvider, MinioStore minio)
			throws IOException, TimeoutException, InterruptedException, VaultException {
		this(
				connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.digest_hasher).newConnection(),
				connectionProvider.getConsulClient(),
				minio,
				new QueueConfiguration(connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.digest_hasher)
						.newConnection().createChannel())
		);
	}
	
	public void processFiles() throws IOException, InterruptedException {
		int processorCount = Runtime.getRuntime().availableProcessors();
		LOGGER.info("System has {} processors", processorCount);
		
		String queueName =  queueConfig.getQueueName(ConfiguredQueues.fileDigest);
		
		LOGGER.info("Starting {} message consumers...", processorCount);
		IntStream.range(0, processorCount).forEach(counter -> {
			try {
				createMessageConsumer(queueName);
			} catch (IOException e) {
				LOGGER.warn("Failed to start message digest consumer: {}", e);
				// TODO Send message with error
				e.printStackTrace();
			}
		});
		
		LOGGER.info("Started message digest consumers");
	}
	
	private void createMessageConsumer(String queueName) throws IOException {
		Channel channel = createChannel();
		channel.basicConsume(queueName, new MessageDigestHashConsumer(channel, minio, queueConfig));
	}

	private Channel createChannel() throws IOException {
		Channel channel = rabbitMqConnection.createChannel();
		channel.basicQos(20);
		
		return channel;
	}
}

class MessageDigestHashConsumer extends DefaultConsumer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MessageDigestHashConsumer.class);

	private final MinioStore minio;
	private final QueueConfiguration queueConfig;
	
	public MessageDigestHashConsumer(Channel channel, MinioStore minio, QueueConfiguration queueConfig) {
		super(channel);
		this.minio = minio;
		this.queueConfig = queueConfig;
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		FileLoad message = FileLoad.parseFrom(body);

		if (message.getRecreateThumbnail()) {
			getChannel().basicAck(envelope.getDeliveryTag(), false);
			return;
		}

		ImagePath imagePath = message.getImagePath();

		String anchor = imagePath.getAnchor();
		String relativePath = imagePath.getRelativePath();
		
		List<String> hashes = message.getMissingHashList();
		
		if (hashes.isEmpty()) {
			LOGGER.debug("No hashes requested for {}:{}, discarding message...", anchor, relativePath);
			getChannel().basicAck(envelope.getDeliveryTag(), false);
			return;
		}
		
		LOGGER.debug("File {}:{} hash request for algorithms: {}", anchor, relativePath, hashes);

		// TODO use InputStream with Memory Digest for more memory efficient processing
		byte[] image = readImage(UUID.fromString(message.getImageId()));

		DbUpdate.Builder builder = DbUpdate.newBuilder();
		builder.getImagePathBuilder().mergeFrom(imagePath);
		builder.setUpdateType(UpdateType.UPDATE_TYPE_DIGEST_HASH);

		for (String hash : hashes) {
			try {
				MessageDigest md = MessageDigest.getInstance(hash);
				builder.putDigestHash(hash, ByteString.copyFrom(md.digest(image)));
			} catch (NoSuchAlgorithmException e) {
				// TODO send a error message back
				e.printStackTrace();
			}
		}
		
		getChannel().basicPublish("", queueConfig.getQueueName(ConfiguredQueues.persistence), null, builder.build().toByteArray());
		getChannel().basicAck(envelope.getDeliveryTag(), false);

		LOGGER.debug("Consumed message for {} - {} > hashes: {}", anchor, relativePath, hashes);
	}

	private byte[] readImage(UUID imageId) throws IOException {
		try {
			InputStream response = minio.getImage(imageId);
			return response.readAllBytes();
		} catch (IllegalArgumentException | IOException | MinioPersistenceException e1) {

			throw new IOException("Failed to read object due to: ", e1);
		}
	}
}