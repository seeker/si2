package com.github.seeker.app;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;
import com.orbitz.consul.model.health.ServiceHealth;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;

/**
 * Loads files from the file system and sends them to the message broker with
 * additional meta data.
 */
public class ThumbnailNode {
	private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailNode.class);

	private static final String THUMBNAIL_DIRECTORY = "thumbs";
	
	private final Channel channel;
	private final MongoDbMapper mapper;
	private final String queueThumbnails;
	
	
	public ThumbnailNode(ConnectionProvider connectionProvider) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", ThumbnailNode.class.getSimpleName());
		
		ConsulClient consul = connectionProvider.getConsulClient();

		queueThumbnails = consul.getKvAsString("config/rabbitmq/queue/thumbnail");

		Connection conn = connectionProvider.getRabbitMQConnection();
		channel = conn.createChannel();
		channel.basicQos(100);

		LOGGER.info("Declaring queue {}", queueThumbnails);
		channel.queueDeclare(queueThumbnails, false, false, false, null);
		
		ServiceHealth mongodbService = consul.getFirstHealtyInstance(ConfiguredService.mongodb);
		
		String database = consul.getKvAsString("config/mongodb/database/si2");
		String mongoDBserverAddress = mongodbService.getNode().getAddress();
		
		MorphiumConfig cfg = new MorphiumConfig();
		LOGGER.info("Conneting to mongodb database {}", database);
		cfg.setDatabase(database);
		cfg.addHostToSeed(mongoDBserverAddress);
				
		Morphium morphium = new Morphium(cfg);
		mapper = new MongoDbMapper(morphium);
		
		startConsumers();
	}

	private void startConsumers() throws IOException {
		LOGGER.info("Starting consumer on queue {}", queueThumbnails);
		channel.basicConsume(queueThumbnails, new ThumbnailStore(channel, mapper, THUMBNAIL_DIRECTORY));
	}
}

class ThumbnailStore extends DefaultConsumer {

	private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailStore.class);

	private final MongoDbMapper mapper;
	private final Path baseThumbnailDirectory;
	
	
	
	public ThumbnailStore(Channel channel, MongoDbMapper mapper, String baseThumbnailDirectory) {
		super(channel);
		
		this.mapper = mapper;
		this.baseThumbnailDirectory = Paths.get(baseThumbnailDirectory);
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		Map<String, Object> header = properties.getHeaders();
		
		String anchor = header.get("anchor").toString();
		Path relativeAnchorPath = Paths.get(header.get("path").toString());
		
		ImageMetaData meta = mapper.getImageMetadata(anchor, relativeAnchorPath);
		
		UUID uuid;
		
		if(meta.hasThumbnail()) {
			uuid = meta.getThumbnailId();
		} else {
			uuid = UUID.randomUUID();
			meta.setThumbnailId(uuid);
		}
		
		Path thumbnailDirectory = generateDirectories(uuid);
		storeThumbnail(thumbnailDirectory, uuid, body);

		mapper.storeDocument(meta);
		LOGGER.info("Stored thumbnail for {} - {} in {}", anchor, relativeAnchorPath, thumbnailDirectory.resolve(uuid.toString()));
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}
	
	private Path generateDirectories(UUID uuid) throws IOException {
		String imageName = uuid.toString();
		
		String firstCharacter = imageName.subSequence(0, 1).toString();
		String firstTwoCharacters = imageName.subSequence(0, 2).toString();
		
		Path thumbnailDirectory = baseThumbnailDirectory.resolve(firstCharacter).resolve(firstTwoCharacters);
		LOGGER.debug("Creating directory {}", thumbnailDirectory);
		
		Files.createDirectories(thumbnailDirectory);
		
		return thumbnailDirectory;
	}
	
	private void storeThumbnail(Path thumbnailDirectory, UUID thumbnailID, byte[] imageData) throws IOException {
		Files.write(thumbnailDirectory.resolve(thumbnailID.toString()), imageData);
	}
}