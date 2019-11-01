package com.github.seeker.app;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;

import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;
import org.jtransforms.dct.DoubleDCT_2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dozedoff.commonj.util.ImageUtil;
import com.github.seeker.commonhash.helper.TransformHelper;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.messaging.HashMessageBuilder;
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

	private final Channel channel;
	private final QueueConfiguration queueConfig;
	
	private final int thumbnailSize;
	private final HashMessageHelper hashMessageHelper;
	
	public ImageResizer(Channel channel, ConsulClient consul, QueueConfiguration queueConfig) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", ImageResizer.class.getSimpleName());
		
		this.channel = channel;
		this.queueConfig = queueConfig;

		hashMessageHelper = new HashMessageHelper();
		thumbnailSize = Integer.parseInt(consul.getKvAsString("config/general/thumbnail-size"));

		this.channel.basicQos(20);
		
		processFiles();
	}
	
	public ImageResizer(ConnectionProvider connectionProvider) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", ImageResizer.class.getSimpleName());
		
		ConsulClient consul = connectionProvider.getConsulClient();
		Connection conn = connectionProvider.getRabbitMQConnection();
		channel = conn.createChannel();
		
		queueConfig = new QueueConfiguration(channel, consul);
		
		hashMessageHelper = new HashMessageHelper();
		thumbnailSize = Integer.parseInt(consul.getKvAsString("config/general/thumbnail-size"));

		channel.basicQos(20);

		
		processFiles();
	}

	public void processFiles() throws IOException, InterruptedException {
		String queueName =  queueConfig.getQueueName(ConfiguredQueues.fileResize);
		LOGGER.info("Starting consumer on queue {}", queueName);
		channel.basicConsume(queueName, new ImageFileMessageConsumer(channel, thumbnailSize, queueConfig));
	}
}

class ImageFileMessageConsumer extends DefaultConsumer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MessageDigestHashConsumer.class);

	//TODO store in custom preprocessor class
	private static final int IMAGE_SIZE = 32; 
	
	private final int thumbnailSize;
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
		BufferedImage thumbnail = Scalr.resize(originalImage, Method.BALANCED, thumbnailSize);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(thumbnail, "jpg", os);
		thumbnail.flush();
		
		Map<String, Object> thumbnailHeaders = new HashMap<String, Object>();
		thumbnailHeaders.put(MessageHeaderKeys.ANCHOR, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR));
		thumbnailHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, receivedMessageHeader.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));
		
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