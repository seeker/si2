package com.github.seeker.configuration;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.github.seeker.persistence.MongoDbMapper;
import com.orbitz.consul.model.health.Service;
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
	
	private static final String PRODUCTION_DB_CONSUL_KEY = "config/mongodb/database/si2";
	public static final String INTEGRATION_DB_CONSUL_KEY = "config/mongodb/database/integration";

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
	
	public Vault getVaultClient() throws VaultException {
		Service vaultSerivce = consul.getFirstHealtyInstance(ConfiguredService.vault).getService();
		VaultConfig vc = new VaultConfig().address(vaultSerivce.getAddress() + ":" + vaultSerivce.getPort()).build();
		return new Vault(vc);
	}
	
	public MongoDbMapper getIntegrationMongoDbMapper() {
		return getMongoDbMapper(INTEGRATION_DB_CONSUL_KEY);
	}
	
	public MongoDbMapper getMongoDbMapper() {
		return getMongoDbMapper(PRODUCTION_DB_CONSUL_KEY);
	}
	
	public Morphium getMorphiumClient(String databaseNameConsulKey) {
		ServiceHealth mongodbService = consul.getFirstHealtyInstance(ConfiguredService.mongodb);
		
		String database = consul.getKvAsString(databaseNameConsulKey);
		String mongoDBserverAddress = mongodbService.getNode().getAddress();

		MorphiumConfig cfg = new MorphiumConfig();
		LOGGER.info("Conneting to mongodb database {}", database);
		cfg.setDatabase(database);
		cfg.addHostToSeed(mongoDBserverAddress);
		//TODO use replica sets at some point, disabled to prevent exception spam
		cfg.setReplicasetMonitoring(false);
				
		return new Morphium(cfg);
	}
	
	public MongoDbMapper getMongoDbMapper(String databaseNameConsulKey) {
		Morphium morphium = getMorphiumClient(databaseNameConsulKey);
		return new MongoDbMapper(morphium);
	}
}
