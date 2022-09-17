package com.github.seeker.app;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;

import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.VaultException;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.MinioConfiguration;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.HashMessageHelper;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.messaging.UUIDUtils;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;

/**
 * Fetches images from the queue and generates thumbnails and resized images for further processing.
 * The results are sent as a new message.
 */
public class ImageResizer {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImageResizer.class);

	private final Connection rabbitMqConnection;
	private final QueueConfiguration queueConfig;
	private final MinioClient minio;
	
	private final int thumbnailSize;
	private final String imageBucket;
	private final String thumbnailBucket;
	private final String preProcessedBucket;
	private final HashMessageHelper hashMessageHelper;
	
	public ImageResizer(Connection channel, ConsulClient consul, QueueConfiguration queueConfig, MinioClient minio, String imageBucket, String thumbnailBucket,
			String preProcessedBucket)
			throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", ImageResizer.class.getSimpleName());
		
		this.rabbitMqConnection = channel;
		this.queueConfig = queueConfig;
		this.minio = minio;
		this.imageBucket = imageBucket;
		this.thumbnailBucket = thumbnailBucket;
		this.preProcessedBucket = preProcessedBucket;

		hashMessageHelper = new HashMessageHelper();
		thumbnailSize = Integer.parseInt(consul.getKvAsString("config/general/thumbnail-size"));

		processFiles();
	}
	
	public ImageResizer(ConnectionProvider connectionProvider) throws IOException, TimeoutException, InterruptedException, VaultException {
		LOGGER.info("{} starting up...", ImageResizer.class.getSimpleName());
		
		ConsulClient consul = connectionProvider.getConsulClient();
		rabbitMqConnection = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.image_resizer).newConnection();
		
		queueConfig = new QueueConfiguration(rabbitMqConnection.createChannel());
		minio = connectionProvider.getMinioClient();

		imageBucket = MinioConfiguration.IMAGE_BUCKET;
		thumbnailBucket = MinioConfiguration.THUMBNAIL_BUCKET;
		preProcessedBucket = MinioConfiguration.PREPROCESSED_IMAGES_BUCKET;
		
		MinioConfiguration.createBucket(minio, imageBucket);
		MinioConfiguration.createBucket(minio, thumbnailBucket);
		MinioConfiguration.createBucket(minio, preProcessedBucket);

		hashMessageHelper = new HashMessageHelper();
		thumbnailSize = Integer.parseInt(consul.getKvAsString("config/general/thumbnail-size"));

		processFiles();
	}

	public void processFiles() throws IOException, InterruptedException {
		int processorCount = Runtime.getRuntime().availableProcessors();
		LOGGER.info("Starting {} message consumers", processorCount);
		String queueName = queueConfig.getQueueName(ConfiguredQueues.fileResize);
		
		IntStream.range(0, processorCount).forEach(count -> {
			try {
				Channel channel = rabbitMqConnection.createChannel();
				channel.basicQos(20);
				LOGGER.info("Starting consumer on queue {}", queueName);
				channel.basicConsume(queueName,
						new ImageFileMessageConsumer(channel, thumbnailSize, queueConfig, minio, imageBucket, thumbnailBucket, preProcessedBucket));
			} catch (IOException e) {
				// TODO send message
				LOGGER.warn("Failed to start consumer: {}", e);
				e.printStackTrace();
			}
		});
	}
}

class ImageFileMessageConsumer extends DefaultConsumer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MessageDigestHashConsumer.class);

	//TODO store in custom preprocessor class
	private static final int IMAGE_SIZE = 32; 
	
	private int thumbnailSize;
	private final QueueConfiguration queueConfig;
	private final MinioClient minio;
	private final String imageBucket;
	private final String thumbnailBucket;
	private final String preProcessedBucket;
	
	public ImageFileMessageConsumer(Channel channel, int thumbnailSize, QueueConfiguration queueConfig, MinioClient minio, String imageBucket,
			String thumbnailBucket, String preProcessedBucket) {
		super(channel);
		
		this.imageBucket = imageBucket;
		this.thumbnailSize = thumbnailSize;
		this.queueConfig = queueConfig;
		this.minio = minio;
		this.thumbnailBucket = thumbnailBucket;
		this.preProcessedBucket = preProcessedBucket;
		
		ImageIO.setUseCache(false);
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		Map<String, Object> receivedMessageHeader = properties.getHeaders();
		
		String anchor = receivedMessageHeader.get(MessageHeaderKeys.ANCHOR).toString();
		String relativePath = receivedMessageHeader.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH).toString();
		Set<String> customHashAlgorithms = Collections.emptySet();
		
		if(receivedMessageHeader.containsKey(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS)) {
			String[] customHash = receivedMessageHeader.get(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS).toString().split(",");

			customHashAlgorithms = Arrays.stream(customHash).filter(ch -> ! ch.isEmpty()).collect(Collectors.toSet());
		}
		
		UUID imageId = UUIDUtils.ByteToUUID(body);
		BufferedImage originalImage;
		
		try (InputStream is = getImageFromBucket(imageId)) {
			originalImage = ImageIO.read(is);
		}catch (IIOException e) {
			LOGGER.warn("Failed to read image {} - {}: {}", anchor, relativePath,e.getMessage());
			getChannel().basicAck(envelope.getDeliveryTag(), false);
			return;
		}
		
		if (originalImage == null) {
			//TODO send an error message
			LOGGER.warn("Was unable to read image data for {} - {} ", anchor, relativePath);
			getChannel().basicAck(envelope.getDeliveryTag(), false);
			return;
		}
		
		boolean hasThumbnail = Boolean.parseBoolean(receivedMessageHeader.get(MessageHeaderKeys.THUMBNAIL_FOUND).toString());
		
		if(!hasThumbnail) {
			LOGGER.debug("{}:{} does not have a thumbnail, creating...", anchor, relativePath);
			try {
				createThumbnail(receivedMessageHeader, originalImage, imageId);
			} catch (IllegalArgumentException iae) {
				//TODO send a error message
				LOGGER.warn("Failed to create thumbnail due to {}", iae);
			} catch (IIOException iioe) {
				//TODO send a error message
				LOGGER.warn("Failed to create thumbnail for {}-{} due to an image error}", anchor, relativePath, iioe);
			}
		}else {
			LOGGER.debug("{}:{} already has a thumbnail, skipping...", anchor, relativePath);
		}
		
		preProcessImage(receivedMessageHeader, originalImage, imageId);
		
		originalImage.flush();
		
		Map<String, Object> hashHeaders = new HashMap<String, Object>();
		hashHeaders.put(MessageHeaderKeys.ANCHOR, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR));
		hashHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));

		LOGGER.debug("Consumed message for {}:{}", anchor, relativePath);
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}

	private InputStream getImageFromBucket(UUID imageId) throws IOException {
		try {
			return minio.getObject(GetObjectArgs.builder().bucket(imageBucket).object(imageId.toString()).build());
		} catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
				| NoSuchAlgorithmException | ServerException | XmlParserException | IllegalArgumentException | IOException e) {
			throw new IOException("Failed to load image " + imageId.toString() + " :", e);
		}
	}

	private void createThumbnail(Map<String, Object> receivedMessageHeader, BufferedImage originalImage, UUID imageId) throws IOException {
		int currentThumbnailSize = this.thumbnailSize;

		BufferedImage thumbnail = Scalr.resize(originalImage, Method.BALANCED, currentThumbnailSize);
		ByteArrayOutputStream baos = new ByteArrayOutputStream(307200);
		ImageIO.write(thumbnail, "jpg", baos);
		thumbnail.flush();

		Map<String, String> metadata = new HashMap<>();
		metadata.put(MessageHeaderKeys.THUMBNAIL_SIZE, String.valueOf(currentThumbnailSize));
		
		try {
			minio.putObject(PutObjectArgs.builder().bucket(thumbnailBucket).object(imageId.toString())
					.stream(new ByteArrayInputStream(baos.toByteArray()), -1, 10485760).userMetadata(metadata).build());
		} catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
				| NoSuchAlgorithmException | ServerException | XmlParserException | IllegalArgumentException | IOException e) {
			throw new IOException("Failed to store thumbnail due to:", e);
		}
		
		// TODO send updates to database node, so thumbnail node can be retired

		Map<String, Object> thumbnailHeaders = new HashMap<String, Object>();
		thumbnailHeaders.put(MessageHeaderKeys.ANCHOR, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR));
		thumbnailHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));
		thumbnailHeaders.put(MessageHeaderKeys.THUMBNAIL_SIZE, currentThumbnailSize);
		
		AMQP.BasicProperties thumbnailProps = new AMQP.BasicProperties.Builder().headers(thumbnailHeaders).build();
		getChannel().basicPublish("", queueConfig.getQueueName(ConfiguredQueues.thumbnails), thumbnailProps, new byte[0]);
	}
	
	private void preProcessImage(Map<String, Object> receivedMessageHeader, BufferedImage originalImage, UUID imageId) throws IOException {
		BufferedImage grayscaleImage = Scalr.resize(originalImage, Method.SPEED, Mode.FIT_EXACT, IMAGE_SIZE,
				new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null));

		ByteArrayOutputStream baos = new ByteArrayOutputStream(307200);

		ImageIO.write(grayscaleImage, "jpg", baos);
		grayscaleImage.flush();

		try {
			minio.putObject(PutObjectArgs.builder().bucket(preProcessedBucket).object(imageId.toString())
					.stream(new ByteArrayInputStream(baos.toByteArray()), -1, 10485760).build());
		} catch (InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
				| NoSuchAlgorithmException | ServerException | XmlParserException | IllegalArgumentException | IOException e) {
			throw new IOException("Failed to store preprocessed image due to:", e);
		}

		Map<String, Object> preProcessedHeaders = new HashMap<String, Object>();
		preProcessedHeaders.put(MessageHeaderKeys.ANCHOR, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR));
		preProcessedHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));
		preProcessedHeaders.put(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS, receivedMessageHeader.get(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS));
		
		AMQP.BasicProperties preprocessedProps = new AMQP.BasicProperties.Builder().headers(preProcessedHeaders).build();
		getChannel().basicPublish("", queueConfig.getQueueName(ConfiguredQueues.filePreProcessed), preprocessedProps, UUIDUtils.UUIDtoByte(imageId));
	}
}