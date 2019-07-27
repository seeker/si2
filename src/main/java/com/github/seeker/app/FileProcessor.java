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
import com.github.seeker.commonhash.perceptual.phash.ImagePHash;
import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.orbitz.consul.model.health.ServiceHealth;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Loads files from the file system and sends them to the message broker with
 * additional meta data.
 */
public class FileProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileProcessor.class);

	private final Channel channel;
	private final String queueFileFeed;
	private final String queueThumbnails;
	private final String queueHashes;
	private final int thumbnailSize;
	
	
	public FileProcessor() throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", FileProcessor.class.getSimpleName());
		ConsulClient consul = new ConsulClient(new ConfigurationBuilder().getConsulConfiguration());
		ServiceHealth rabbitmqService = consul.getFirstHealtyInstance(ConfiguredService.rabbitmq);

		String serverAddress = rabbitmqService.getNode().getAddress();
		int serverPort = rabbitmqService.getService().getPort();

		ConnectionFactory connFactory = new ConnectionFactory();
		connFactory.setUsername("si2");
		connFactory.setPassword(consul.getKvAsString("config/rabbitmq/users/si2"));
		connFactory.setHost(serverAddress);
		connFactory.setPort(serverPort);

		queueFileFeed = consul.getKvAsString("config/rabbitmq/queue/loader-file-feed");
		queueThumbnails = consul.getKvAsString("config/rabbitmq/queue/thumbnail");
		queueHashes = consul.getKvAsString("config/rabbitmq/queue/hash");
		thumbnailSize = Integer.parseInt(consul.getKvAsString("config/general/thumbnail-size"));

		LOGGER.info("Connecting to Rabbitmq server {}:{}", serverAddress, serverPort);

		Connection conn = connFactory.newConnection();
		channel = conn.createChannel();

		LOGGER.info("Declaring queue {}", queueFileFeed);
		channel.queueDeclare(queueFileFeed, false, false, false, null);
		channel.basicQos(100);

		LOGGER.info("Declaring queue {}", queueThumbnails);
		channel.queueDeclare(queueThumbnails, false, false, false, null);
		
		LOGGER.info("Declaring queue {}", queueHashes);
		channel.queueDeclare(queueHashes, false, false, false, null);
		
		processFiles();
	}

	public void processFiles() throws IOException, InterruptedException {
		LOGGER.info("Starting consumer on queue {}", queueFileFeed);
		channel.basicConsume(queueFileFeed, new FileMessageConsumer(channel, thumbnailSize, queueThumbnails, queueHashes));
	}
}

class FileMessageConsumer extends DefaultConsumer {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileMessageConsumer.class);

	private static final int IMAGE_SIZE = 32;
	private static final int DCT_MATRIX_SIZE = 8;
	
	private final DoubleDCT_2D jtransformDCT;
	
	private final int thumbnailSize;
	private final String queueThumbnails;
	private final String queueHashes;
	
	public FileMessageConsumer(Channel channel, int thumbnailSize, String queueThumbnails, String queueHashes) {
		super(channel);
		
		this.thumbnailSize = thumbnailSize;
		this.queueThumbnails = queueThumbnails;
		this.queueHashes = queueHashes;
		
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
			LOGGER.warn("Was unable to read image data for {} - {} ", header.get("anchor"), header.get("path"));
			getChannel().basicAck(envelope.getDeliveryTag(), false);
			return;
		}
		
		BufferedImage thumbnail = Scalr.resize(originalImage, Method.BALANCED, thumbnailSize);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(thumbnail, "jpg", os);
		thumbnail.flush();
		
		Map<String, Object> thumbnailHeaders = new HashMap<String, Object>();
		thumbnailHeaders.put("anchor", header.get("anchor"));
		thumbnailHeaders.put("path", header.get("path"));
		
		AMQP.BasicProperties thumbnailProps = new AMQP.BasicProperties.Builder().headers(thumbnailHeaders).build();
		getChannel().basicPublish("", queueThumbnails, thumbnailProps, os.toByteArray());
		
		long pHash = calculatePhash(originalImage);
		originalImage.flush();
		
		Map<String, Object> hashHeaders = new HashMap<String, Object>();
		hashHeaders.put("anchor", header.get("anchor"));
		hashHeaders.put("path", header.get("path"));
		hashHeaders.put("pHash", pHash);
		hashHeaders.put("sha256", dis.getMessageDigest().digest());
		
		AMQP.BasicProperties hashProps = new AMQP.BasicProperties.Builder().headers(hashHeaders).build();
		getChannel().basicPublish("", queueHashes, hashProps, null);
		
		LOGGER.debug("Consumed message for {} - {} > hashes: {}", header.get("anchor"), header.get("path"),	header.get("missing-hash"));
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
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