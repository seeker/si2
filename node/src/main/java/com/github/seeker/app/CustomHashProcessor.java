package com.github.seeker.app;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;

import org.jtransforms.dct.DoubleDCT_2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.VaultException;
import com.github.dozedoff.commonj.util.ImageUtil;
import com.github.seeker.commonhash.helper.TransformHelper;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.HashMessageBuilder;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.messaging.UUIDUtils;
import com.github.seeker.persistence.MinioPersistenceException;
import com.github.seeker.persistence.MinioStore;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Fetches images from the queue and generates hashes and the thumbnail.
 * The results are sent as a new message.
 */
public class CustomHashProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(CustomHashProcessor.class);

	private final Channel channel;
	private final MinioStore minio;
	private final QueueConfiguration queueConfig;
	
	public CustomHashProcessor(Channel channel, ConsulClient consul, MinioStore minio, QueueConfiguration queueConfig)
			throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", CustomHashProcessor.class.getSimpleName());
		
		this.channel = channel;
		this.queueConfig = queueConfig;
		this.minio = minio;
		
		channel.basicQos(20);
		
		processFiles();
	}
	
	public CustomHashProcessor(ConnectionProvider connectionProvider, MinioStore minio)
			throws IOException, TimeoutException, InterruptedException, VaultException {
		LOGGER.info("{} starting up...", CustomHashProcessor.class.getSimpleName());
		
		Connection conn = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.hash_processor).newConnection();
		this.minio = minio;
		channel = conn.createChannel();
		
		queueConfig = new QueueConfiguration(channel);
		
		channel.basicQos(20);

		processFiles();
	}

	public void processFiles() throws IOException, InterruptedException {
		String queueName =  queueConfig.getQueueName(ConfiguredQueues.filePreProcessed);
		LOGGER.info("Starting consumer on queue {}", queueName);
		channel.basicConsume(queueName, new CustomFileMessageConsumer(channel, queueConfig, minio));
	}
}

class CustomFileMessageConsumer extends DefaultConsumer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MessageDigestHashConsumer.class);

	private static final int IMAGE_SIZE = 32;
	private static final int DCT_MATRIX_SIZE = 8;
	
	private final DoubleDCT_2D jtransformDCT;
	
	private final QueueConfiguration queueConfig;
	private final HashMessageBuilder hashMessageBuilder;
	private final MinioStore minio;
	
	public CustomFileMessageConsumer(Channel channel, QueueConfiguration queueConfig, MinioStore minio) {
		super(channel);
		
		this.queueConfig = queueConfig;
		this.hashMessageBuilder = new HashMessageBuilder(channel, queueConfig);
		this.minio = minio;

		this.jtransformDCT = new DoubleDCT_2D(IMAGE_SIZE, IMAGE_SIZE); 
		
		ImageIO.setUseCache(false);
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		Map<String, Object> header = properties.getHeaders();
		
		String anchor = header.get(MessageHeaderKeys.ANCHOR).toString();
		String relativePath = header.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH).toString();
		Object rawCustomHashHeader = header.get(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS);
		String[] customHashes = null;
		
		if (Objects.nonNull(rawCustomHashHeader)) {
			customHashes = rawCustomHashHeader.toString().split(",");
		} else {
			customHashes = new String[0];
		}
		
		if(! hasCustomHashes(customHashes)) {
			getChannel().basicAck(envelope.getDeliveryTag(), false);
			return;
		}
		
		UUID imageId = UUIDUtils.ByteToUUID(body);
		
		try (InputStream response = minio.getPreProcessedImage(imageId)) {
		
		BufferedImage preProcessedImage;
		
		try {
			preProcessedImage = ImageIO.read(response);
		} catch (IIOException e) {
			LOGGER.warn("Failed to read image {}:{}: {}", anchor, relativePath, e.getMessage());
			getChannel().basicNack(envelope.getDeliveryTag(), false, false);
			return;
		}

		if (preProcessedImage == null) {
			// TODO send an error message
			LOGGER.warn("Was unable to read image data for {}:{} ", anchor, relativePath);
			getChannel().basicNack(envelope.getDeliveryTag(), false, false);
			return;
		}

		long pHash = calculatePhash(preProcessedImage);
		preProcessedImage.flush();

		Map<String, Object> hashHeaders = new HashMap<String, Object>();
		hashHeaders.put(MessageHeaderKeys.ANCHOR, header.get(MessageHeaderKeys.ANCHOR));
		hashHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, header.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));
		hashHeaders.put(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS, header.get(MessageHeaderKeys.CUSTOM_HASH_ALGORITHMS));

		ByteArrayDataOutput messageBody = ByteStreams.newDataOutput();
		messageBody.writeLong(pHash);

		// TODO include custom hashes in HashMessageBuilder
		AMQP.BasicProperties hashProps = new AMQP.BasicProperties.Builder().headers(hashHeaders).build();
		getChannel().basicPublish("", queueConfig.getQueueName(ConfiguredQueues.hashes), hashProps, messageBody.toByteArray());

		LOGGER.debug("Consumed message for {} - {} > hashes: {}", header.get("anchor"), header.get("path"), header.get("missing-hash"));

		getChannel().basicAck(envelope.getDeliveryTag(), false);
	} catch (IllegalArgumentException | MinioPersistenceException e1) {
		getChannel().basicNack(envelope.getDeliveryTag(), false, false);
		throw new IOException("Failed to load preprocessed image:", e1);
	}
}
	
	private boolean hasCustomHashes(String[] customHashes) {
		return ! (customHashes.length == 0 || customHashes[0].isEmpty());
	}
	
	private long calculatePhash(BufferedImage originalImage) {
		double[][] reducedColorValues = ImageUtil.toDoubleMatrix(originalImage);

		jtransformDCT.forward(reducedColorValues, true);
		double[][] dct = reducedColorValues;
		
		double dctAvg = TransformHelper.dctAverage(dct, DCT_MATRIX_SIZE);
		
		return convertToLong(dct, dctAvg);
	}
	
	private long convertToLong(double[][] dctVals, double avg) {
		long hash = 0;

		for (int x = 0; x < DCT_MATRIX_SIZE; x++) {
			for (int y = 0; y < DCT_MATRIX_SIZE; y++) {
				hash += (dctVals[x][y] > avg ? 1 : 0);
				hash = Long.rotateLeft(hash, 1);
			}
		}

		return hash;
	}
}