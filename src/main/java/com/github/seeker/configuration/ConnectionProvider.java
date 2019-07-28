package com.github.seeker.configuration;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.github.seeker.persistence.MongoDbMapper;
import com.orbitz.consul.model.health.ServiceHealth;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;

/**
 * Provides configured connections for used services. 
 */
public class ConnectionProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionProvider.class);
	private final ConsulClient consul;
	private final ConsulConfiguration consulConfig;

	public ConnectionProvider(ConsulConfiguration consulConfig) {
		this.consul = new ConsulClient(consulConfig);
		this.consulConfig = consulConfig;
	}
	
	public ConsulClient getConsulClient() {
		return new ConsulClient(consulConfig);
	}

	public Connection getRabbitMQConnection() throws IOException, TimeoutException {
		ServiceHealth rabbitmqService = consul.getFirstHealtyInstance(ConfiguredService.rabbitmq);

		String serverAddress = rabbitmqService.getNode().getAddress();
		int serverPort = rabbitmqService.getService().getPort();

		ConnectionFactory connFactory = new ConnectionFactory();
		connFactory.setUsername("si2");
		connFactory.setPassword(consul.getKvAsString("config/rabbitmq/users/si2"));
		connFactory.setHost(serverAddress);
		connFactory.setPort(serverPort);

		LOGGER.info("Connecting to Rabbitmq server {}:{}", serverAddress, serverPort);

		return connFactory.newConnection();
	}
	
	public MongoDbMapper getMongoDbMapper() {
		ServiceHealth mongodbService = consul.getFirstHealtyInstance(ConfiguredService.mongodb);
		
		String database = consul.getKvAsString("config/mongodb/database/si2");
		String mongoDBserverAddress = mongodbService.getNode().getAddress();

		MorphiumConfig cfg = new MorphiumConfig();
		LOGGER.info("Conneting to mongodb database {}", database);
		cfg.setDatabase(database);
		cfg.addHostToSeed(mongoDBserverAddress);
				
		Morphium morphium = new Morphium(cfg);
		return new MongoDbMapper(morphium);
	}
}
