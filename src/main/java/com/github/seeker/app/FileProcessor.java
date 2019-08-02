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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

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
import com.github.seeker.messaging.HashMessageHelper;
import com.github.seeker.messaging.MessageHeaderKeys;
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
public class FileProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileProcessor.class);

	private final Channel channel;
	private final QueueConfiguration queueConfig;
	
	private final int thumbnailSize;
	
	
	public FileProcessor(ConnectionProvider connectionProvider) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", FileProcessor.class.getSimpleName());
		
		ConsulClient consul = connectionProvider.getConsulClient();
		Connection conn = connectionProvider.getRabbitMQConnection();
		channel = conn.createChannel();
		
		queueConfig = new QueueConfiguration(channel, consul);
		
		thumbnailSize = Integer.parseInt(consul.getKvAsString("config/general/thumbnail-size"));

		channel.basicQos(100);

		
		processFiles();
	}

	public void processFiles() throws IOException, InterruptedException {
		String queueName =  queueConfig.getQueueName(ConfiguredQueues.files);
		LOGGER.info("Starting consumer on queue {}", queueName);
		channel.basicConsume(queueName, new FileMessageConsumer(channel, thumbnailSize, queueConfig));
	}
}

class FileMessageConsumer extends DefaultConsumer {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileMessageConsumer.class);

	private static final int IMAGE_SIZE = 32;
	private static final int DCT_MATRIX_SIZE = 8;
	
	private final DoubleDCT_2D jtransformDCT;
	
	private final int thumbnailSize;
	private final QueueConfiguration queueConfig;
	private final HashMessageHelper hashMessageHelper;
	
	public FileMessageConsumer(Channel channel, int thumbnailSize, QueueConfiguration queueConfig) {
		super(channel);
		
		this.thumbnailSize = thumbnailSize;
		this.queueConfig = queueConfig;
		this.hashMessageHelper = new HashMessageHelper(channel, queueConfig);
		
		this.jtransformDCT = new DoubleDCT_2D(IMAGE_SIZE, IMAGE_SIZE); 
		
		ImageIO.setUseCache(false);
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		Map<String, Object> header = properties.getHeaders();
		
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			//TODO send a error message back
			e.printStackTrace();
		}
		
		InputStream is =  new ByteArrayInputStream(body);
		DigestInputStream dis =  new DigestInputStream(is, md);
		
		BufferedImage originalImage = ImageIO.read(dis);
		
		if (originalImage == null) {
			//TODO send an error message
			LOGGER.warn("Was unable to read image data for {} - {} ", header.get(MessageHeaderKeys.ANCHOR), header.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));
			getChannel().basicAck(envelope.getDeliveryTag(), false);
			return;
		}
		
		boolean hasThumbnail = Boolean.parseBoolean(header.get("thumb").toString());
		
		if(!hasThumbnail) {
			createThumbnail(header, originalImage);
		}
		
		long pHash = calculatePhash(originalImage);
		originalImage.flush();
		
		Map<String, Object> hashHeaders = new HashMap<String, Object>();
		hashHeaders.put(MessageHeaderKeys.ANCHOR, header.get(MessageHeaderKeys.ANCHOR));
		hashHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, header.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));

		hashMessageHelper.sendMessage(hashHeaders, dis.getMessageDigest().digest(), pHash);
		
		LOGGER.debug("Consumed message for {} - {} > hashes: {}", header.get("anchor"), header.get("path"),	header.get("missing-hash"));
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}

	private void createThumbnail(Map<String, Object> header, BufferedImage originalImage) throws IOException {
		BufferedImage thumbnail = Scalr.resize(originalImage, Method.BALANCED, thumbnailSize);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(thumbnail, "jpg", os);
		thumbnail.flush();
		
		Map<String, Object> thumbnailHeaders = new HashMap<String, Object>();
		thumbnailHeaders.put("anchor", header.get("anchor"));
		thumbnailHeaders.put("path", header.get("path"));
		
		AMQP.BasicProperties thumbnailProps = new AMQP.BasicProperties.Builder().headers(thumbnailHeaders).build();
		getChannel().basicPublish("", queueConfig.getQueueName(ConfiguredQueues.thumbnails), thumbnailProps, os.toByteArray());
	}
	
	private long calculatePhash(BufferedImage originalImage) {
		BufferedImage grayscaleImage = Scalr.resize(originalImage, Method.SPEED, Mode.FIT_EXACT, IMAGE_SIZE, new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null));
		
		double[][] reducedColorValues = ImageUtil.toDoubleMatrix(grayscaleImage);

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