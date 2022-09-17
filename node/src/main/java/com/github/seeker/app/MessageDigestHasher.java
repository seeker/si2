package com.github.seeker.app;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.VaultException;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.MinioConfiguration;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.HashMessageBuilder;
import com.github.seeker.messaging.HashMessageHelper;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.messaging.UUIDUtils;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;

/**
 * Fetches images from the queue and generates hashes based on the requested Algorithms using {@link MessageDigest}.
 * The results are sent as a new message.
 */
public class MessageDigestHasher {
	private static final Logger LOGGER = LoggerFactory.getLogger(MessageDigestHasher.class);

	private final Connection rabbitMqConnection;
	private final MinioClient minio;
	private final QueueConfiguration queueConfig;
	private final String imageBucket;
	
	public MessageDigestHasher(Connection rabbitMqConnection, ConsulClient consul, MinioClient minio, QueueConfiguration queueConfig, String imageBucket)
			throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", MessageDigestHasher.class.getSimpleName());
		
		this.rabbitMqConnection = rabbitMqConnection;
		this.minio = minio;
		this.queueConfig = queueConfig;
		this.imageBucket = imageBucket;
		
		processFiles();
	}
	
	public MessageDigestHasher(ConnectionProvider connectionProvider) throws IOException, TimeoutException, InterruptedException, VaultException {
		this(
				connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.digest_hasher).newConnection(),
				connectionProvider.getConsulClient(),
				connectionProvider.getMinioClient(),
				new QueueConfiguration(connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.digest_hasher).newConnection().createChannel()),
				MinioConfiguration.IMAGE_BUCKET
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
		channel.basicConsume(queueName, new MessageDigestHashConsumer(channel, minio, queueConfig, imageBucket));
	}

	private Channel createChannel() throws IOException {
		Channel channel = rabbitMqConnection.createChannel();
		channel.basicQos(20);
		
		return channel;
	}
}

class MessageDigestHashConsumer extends DefaultConsumer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MessageDigestHashConsumer.class);

	private final HashMessageBuilder hashMessageBuilder;
	private final HashMessageHelper hashMessageHelper;
	private final MinioClient minio;
	private final String imageBucket;
	
	public MessageDigestHashConsumer(Channel channel, MinioClient minio, QueueConfiguration queueConfig, String imageBucket) {
		super(channel);
		this.minio = minio;
		this.hashMessageBuilder = new HashMessageBuilder(channel, queueConfig);
		this.hashMessageHelper = new HashMessageHelper();
		this.imageBucket = imageBucket;
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		Map<String, Object> originalHeader = properties.getHeaders();
		
		String anchor = hashMessageHelper.getAnchor(originalHeader);
		String relativePath = hashMessageHelper.getRelativePath(originalHeader);
		
		String requiredHashes = originalHeader.get(MessageHeaderKeys.HASH_ALGORITHMS).toString();
		String[] hashes = requiredHashes.split(",");
		
		if (hashes.length == 1 && "".equals(hashes[0])) {
			LOGGER.debug("No hashes requested for {}:{}, discarding message...", anchor, relativePath);
			getChannel().basicAck(envelope.getDeliveryTag(), false);
			return;
		}
		
		LOGGER.debug("File {}:{} hash request for algorithms: {}", anchor, relativePath, hashes);
		
		UUID imagId = UUIDUtils.ByteToUUID(body);
		try {
			minio.getObject(GetObjectArgs.builder().bucket(MinioConfiguration.IMAGE_BUCKET).object(imagId.toString()).build());
		} catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
				| NoSuchAlgorithmException | ServerException | XmlParserException | IllegalArgumentException | IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		// TODO use InputStream with Memory Digest for more memory efficient processing
		byte[] image = readImage(body);

		for (String hash : hashes) {
			try {
				MessageDigest md = MessageDigest.getInstance(hash);
				hashMessageBuilder.addHash(hash, md.digest(image));
			} catch (NoSuchAlgorithmException e) {
				// TODO send a error message back
				e.printStackTrace();
			}
		}
		
		hashMessageBuilder.send(originalHeader);
		LOGGER.debug("Consumed message for {} - {} > hashes: {}", originalHeader.get("anchor"), originalHeader.get("path"),	hashes);
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}

	private byte[] readImage(byte[] messageBody) throws IOException {
		UUID imageId = UUIDUtils.ByteToUUID(messageBody);
		try {
			GetObjectResponse response = minio.getObject(GetObjectArgs.builder().bucket(imageBucket).object(imageId.toString()).build());

			return response.readAllBytes();
		} catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
				| NoSuchAlgorithmException | ServerException | XmlParserException | IllegalArgumentException | IOException e1) {

			throw new IOException("Failed to read object due to: ", e1);
		}
	}
}