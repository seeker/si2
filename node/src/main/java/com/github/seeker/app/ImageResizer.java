package com.github.seeker.app;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.HashMessageHelper;
import com.github.seeker.messaging.MessageHeaderKeys;
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
	
	private final int thumbnailSize;
	private final HashMessageHelper hashMessageHelper;
	
	public ImageResizer(Connection channel, ConsulClient consul, QueueConfiguration queueConfig) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", ImageResizer.class.getSimpleName());
		
		this.rabbitMqConnection = channel;
		this.queueConfig = queueConfig;

		hashMessageHelper = new HashMessageHelper();
		thumbnailSize = Integer.parseInt(consul.getKvAsString("config/general/thumbnail-size"));

		processFiles();
	}
	
	public ImageResizer(ConnectionProvider connectionProvider) throws IOException, TimeoutException, InterruptedException, VaultException {
		LOGGER.info("{} starting up...", ImageResizer.class.getSimpleName());
		
		ConsulClient consul = connectionProvider.getConsulClient();
		rabbitMqConnection = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.image_resizer).newConnection();
		
		queueConfig = new QueueConfiguration(rabbitMqConnection.createChannel(), consul);
		
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
				channel.basicConsume(queueName, new ImageFileMessageConsumer(channel, thumbnailSize, queueConfig));
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
	
	public ImageFileMessageConsumer(Channel channel, int thumbnailSize, QueueConfiguration queueConfig) {
		super(channel);
		
		this.thumbnailSize = thumbnailSize;
		this.queueConfig = queueConfig;
		
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
		
		InputStream is =  new ByteArrayInputStream(body);
		
		BufferedImage originalImage;
		
		try {
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
				createThumbnail(receivedMessageHeader, originalImage);
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
		
		preProcessImage(receivedMessageHeader, originalImage);
		
		originalImage.flush();
		
		Map<String, Object> hashHeaders = new HashMap<String, Object>();
		hashHeaders.put(MessageHeaderKeys.ANCHOR, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR));
		hashHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));

		LOGGER.debug("Consumed message for {}:{}", anchor, relativePath);
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}

	private void createThumbnail(Map<String, Object> receivedMessageHeader, BufferedImage originalImage) throws IOException {
		int currentThumbnailSize = this.thumbnailSize;
		
		BufferedImage thumbnail = Scalr.resize(originalImage, Method.BALANCED, currentThumbnailSize);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(thumbnail, "jpg", os);
		thumbnail.flush();
		
		Map<String, Object> thumbnailHeaders = new HashMap<String, Object>();
		thumbnailHeaders.put(MessageHeaderKeys.ANCHOR, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR));
		thumbnailHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));
		thumbnailHeaders.put(MessageHeaderKeys.THUMBNAIL_SIZE, currentThumbnailSize);

		
		AMQP.BasicProperties thumbnailProps = new AMQP.BasicProperties.Builder().headers(thumbnailHeaders).build();
		getChannel().basicPublish("", queueConfig.getQueueName(ConfiguredQueues.thumbnails), thumbnailProps, os.toByteArray());
	}
	
	private void preProcessImage(Map<String, Object> receivedMessageHeader, BufferedImage originalImage) throws IOException {
		BufferedImage grayscaleImage = Scalr.resize(originalImage, Method.SPEED, Mode.FIT_EXACT, IMAGE_SIZE, new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null));
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(grayscaleImage, "jpg", os);
		grayscaleImage.flush();
		
		Map<String, Object> preProcessedHeaders = new HashMap<String, Object>();
		preProcessedHeaders.put(MessageHeaderKeys.ANCHOR, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR));
		preProcessedHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));
		preProcessedHeaders.put(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS, receivedMessageHeader.get(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS));
		
		AMQP.BasicProperties preprocessedProps = new AMQP.BasicProperties.Builder().headers(preProcessedHeaders).build();
		getChannel().basicPublish("", queueConfig.getQueueName(ConfiguredQueues.filePreProcessed), preprocessedProps, os.toByteArray());
	}
}