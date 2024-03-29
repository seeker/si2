package com.github.seeker.app;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
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
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.proto.DbUpdateOuterClass.DbUpdate;
import com.github.seeker.messaging.proto.DbUpdateOuterClass.UpdateType;
import com.github.seeker.messaging.proto.FileLoadOuterClass.FileLoad;
import com.github.seeker.messaging.proto.ImagePathOuterClass.ImagePath;
import com.github.seeker.persistence.MinioPersistenceException;
import com.github.seeker.persistence.MinioStore;
import com.orbitz.consul.cache.KVCache;
import com.orbitz.consul.model.kv.Value;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Fetches images from the queue and generates thumbnails and resized images for further processing.
 * The results are sent as a new message.
 */
public class ImageResizer {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImageResizer.class);

	private final Connection rabbitMqConnection;
	private final QueueConfiguration queueConfig;
	private final MinioStore minio;
	private final ConsulClient consul;
	
	public ImageResizer(Connection channel, ConsulClient consul, QueueConfiguration queueConfig, MinioStore minio)
			throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", ImageResizer.class.getSimpleName());
		
		this.rabbitMqConnection = channel;
		this.queueConfig = queueConfig;
		this.minio = minio;
		this.consul = consul;

		processFiles();
	}
	
	public ImageResizer(ConnectionProvider connectionProvider, MinioStore minio)
			throws IOException, TimeoutException, InterruptedException, VaultException {
		LOGGER.info("{} starting up...", ImageResizer.class.getSimpleName());
		
		consul = connectionProvider.getConsulClient();
		rabbitMqConnection = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.image_resizer).newConnection();
		
		queueConfig = new QueueConfiguration(rabbitMqConnection.createChannel());
		this.minio = minio;

		minio.createBuckets();

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
						new ImageFileMessageConsumer(channel, consul, queueConfig, minio));
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
	private final MinioStore minio;
	
	public ImageFileMessageConsumer(Channel channel, ConsulClient consul, QueueConfiguration queueConfig,
			MinioStore minio) {
		super(channel);
		
		this.queueConfig = queueConfig;
		this.minio = minio;
		
		final String thumbnailSizeKVpath = "config/general/thumbnail-size";

		this.thumbnailSize = (int) consul.getKvAsLong(thumbnailSizeKVpath);
		LOGGER.info("Set thumbnail size to {}", thumbnailSize);

		KVCache rateLimitCache = consul.getKVCache(thumbnailSizeKVpath);
		rateLimitCache.addListener(newValues -> {
			Optional<Value> newValue = newValues.values().stream().filter(value -> value.getKey().equals(thumbnailSizeKVpath)).findAny();

			newValue.ifPresent(value -> {
				Optional<String> decodedThumbSize = newValue.get().getValueAsString();
				decodedThumbSize.ifPresent(size -> {
					this.thumbnailSize = Integer.parseInt(size);
					LOGGER.info("Updated thumbnail size to {}", thumbnailSize);
				});
			});
		});

		rateLimitCache.start();

		ImageIO.setUseCache(false);
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		FileLoad message = FileLoad.parseFrom(body);
		ImagePath imagePath = message.getImagePath();

		String anchor = imagePath.getAnchor();
		String relativePath = imagePath.getRelativePath();
		
		UUID imageId = UUID.fromString(message.getImageId());
		BufferedImage originalImage;
		
		try (InputStream is = getImageFromBucket(imageId)) {
			originalImage = ImageIO.read(is);
		} catch (IOException | IllegalArgumentException e) {
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
		
		if (message.getRecreateThumbnail()) {
			LOGGER.debug("Recreating thumbnail for {} - {} with size {}", anchor, relativePath, thumbnailSize);
			try {
				createThumbnail(message, originalImage);
				getChannel().basicAck(envelope.getDeliveryTag(), false);
				return;
			} catch (IllegalArgumentException iae) {
				// TODO send a error message
				LOGGER.warn("Failed to create thumbnail due to {}", iae);
			} catch (IIOException iioe) {
				// TODO send a error message
				LOGGER.warn("Failed to create thumbnail for {}-{} due to an image error}", anchor, relativePath, iioe);
			}

			getChannel().basicAck(envelope.getDeliveryTag(), false);
			return;
		}
		
		if (message.getGenerateThumbnail()) {
			LOGGER.debug("{}:{} does not have a thumbnail, creating...", anchor, relativePath);
			try {
				createThumbnail(message, originalImage);
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
		
		preProcessImage(message, originalImage);
		
		originalImage.flush();

		LOGGER.debug("Consumed message for {}:{}", anchor, relativePath);
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}

	private InputStream getImageFromBucket(UUID imageId) throws IOException {
		try {
			return minio.getImage(imageId);
		} catch (IllegalArgumentException | MinioPersistenceException e) {
			throw new IOException("Failed to load image " + imageId.toString() + " :", e);
		}
	}

	private void createThumbnail(FileLoad message, BufferedImage originalImage) throws IOException {
		int currentThumbnailSize = this.thumbnailSize;

		BufferedImage thumbnail = Scalr.resize(originalImage, Method.BALANCED, currentThumbnailSize);
		ByteArrayOutputStream baos = new ByteArrayOutputStream(307200);
		ImageIO.write(thumbnail, "jpg", baos);
		thumbnail.flush();
		
		try {
			// TODO need to store metadata?
			minio.storeThumbnail(UUID.fromString(message.getImageId()), new ByteArrayInputStream(baos.toByteArray()));
		} catch (IllegalArgumentException | MinioPersistenceException e) {
			throw new IOException("Failed to store thumbnail due to:", e);
		}
		
		DbUpdate.Builder builder = DbUpdate.newBuilder();
		builder.getImagePathBuilder().mergeFrom(message.getImagePath());
		builder.setUpdateType(UpdateType.UPDATE_TYPE_THUMBNAIL);
		builder.setThumbnailSize(currentThumbnailSize);
		
		getChannel().basicPublish("", queueConfig.getQueueName(ConfiguredQueues.persistence), emptyHeader(), builder.build().toByteArray());
	}
	
	private void preProcessImage(FileLoad message, BufferedImage originalImage) throws IOException {
		BufferedImage grayscaleImage = Scalr.resize(originalImage, Method.SPEED, Mode.FIT_EXACT, IMAGE_SIZE,
				new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null));

		ByteArrayOutputStream baos = new ByteArrayOutputStream(307200);

		ImageIO.write(grayscaleImage, "jpg", baos);
		grayscaleImage.flush();

		try {
			minio.storePreProcessedImage(UUID.fromString(message.getImageId()), new ByteArrayInputStream(baos.toByteArray()));
		} catch (IllegalArgumentException | MinioPersistenceException e) {
			throw new IOException("Failed to store preprocessed image due to:", e);
		}

		getChannel().basicPublish("", queueConfig.getQueueName(ConfiguredQueues.filePreProcessed), emptyHeader(), message.toByteArray());
	}

	private BasicProperties emptyHeader() {
		return new AMQP.BasicProperties.Builder().headers(Collections.emptyMap()).build();
	}
}