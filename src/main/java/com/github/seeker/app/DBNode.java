package com.github.seeker.app;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.Hash;
import com.github.seeker.persistence.document.ImageMetaData;
import com.google.common.primitives.Longs;
import com.orbitz.consul.model.health.ServiceHealth;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;

/**
 * Loads files from the file system and sends them to the message broker with
 * additional meta data.
 */
public class DBNode {
	private static final Logger LOGGER = LoggerFactory.getLogger(DBNode.class);

	private final Channel channel;
	private final MongoDbMapper mapper;
	private final String queueHash;
	private final List<String> requiredHashes;
	
	public DBNode(ConnectionProvider connectionProvider) throws IOException, TimeoutException, InterruptedException {
		LOGGER.info("{} starting up...", DBNode.class.getSimpleName());
		
		ConsulClient consul = connectionProvider.getConsulClient();
		Connection conn = connectionProvider.getRabbitMQConnection();
		
		queueHash = consul.getKvAsString("config/rabbitmq/queue/hash");

		channel = conn.createChannel();
		channel.basicQos(100);

		LOGGER.info("Declaring queue {}", queueHash);
		channel.queueDeclare(queueHash, false, false, false, null);
		
		ServiceHealth mongodbService = consul.getFirstHealtyInstance(ConfiguredService.mongodb);
		
		String database = consul.getKvAsString("config/mongodb/database/si2");
		String mongoDBserverAddress = mongodbService.getNode().getAddress();

		requiredHashes = Arrays.asList(consul.getKvAsString("config/general/required-hashes").split(Pattern.quote(",")));

		MorphiumConfig cfg = new MorphiumConfig();
		LOGGER.info("Conneting to mongodb database {}", database);
		cfg.setDatabase(database);
		cfg.addHostToSeed(mongoDBserverAddress);
				
		Morphium morphium = new Morphium(cfg);
		mapper = new MongoDbMapper(morphium);
		
		startConsumers();
	}

	private void startConsumers() throws IOException {
		LOGGER.info("Starting consumer on queue {}", queueHash);
		channel.basicConsume(queueHash, new DBStore(channel, mapper, requiredHashes));
	}
}

class DBStore extends DefaultConsumer {

	private static final Logger LOGGER = LoggerFactory.getLogger(DBStore.class);

	private final MongoDbMapper mapper;
	private final List<String> requiredHashes;
	
	
	public DBStore(Channel channel, MongoDbMapper mapper, List<String> requiredHashes) {
		super(channel);
		
		this.mapper = mapper;
		this.requiredHashes = requiredHashes;
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
		Map<String, Object> header = properties.getHeaders();
		
		String anchor = header.get("anchor").toString();
		Path relativeAnchorPath = Paths.get(header.get("path").toString());
		
		ImageMetaData meta = mapper.getImageMetadata(anchor, relativeAnchorPath);
		
		updateMetadata(meta, header);

		mapper.storeDocument(meta);
		LOGGER.info("Updated database entry for {} - {}", anchor, relativeAnchorPath);
		
		getChannel().basicAck(envelope.getDeliveryTag(), false);
	}
	
	private void updateMetadata(ImageMetaData meta, Map<String, Object> header) {
		//TODO use required hashes
		
		Map<String, Hash> metaHashes = meta.getHashes();
		
		metaHashes.put("sha256", new Hash(header.get("sha256").toString().getBytes()));
		metaHashes.put("phash", new Hash(Longs.toByteArray(Long.parseLong(header.get("phash").toString()))));
		
		mapper.storeDocument(meta);
	}
}