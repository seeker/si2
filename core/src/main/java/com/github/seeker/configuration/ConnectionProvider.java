package com.github.seeker.configuration;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.AuthResponse;
import com.bettercloud.vault.response.LogicalResponse;
import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.github.seeker.persistence.MongoDbMapper;
import com.orbitz.consul.model.health.Node;
import com.orbitz.consul.model.health.Service;
import com.orbitz.consul.model.health.ServiceHealth;
import com.rabbitmq.client.ConnectionFactory;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import io.minio.MinioClient;

/**
 * Provides configured connections for used services. 
 */
public class ConnectionProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionProvider.class);
	private final ConsulClient consul;
	private final Vault vault;
	private final VaultWorkaround vaultWA;
	private final ConsulConfiguration consulConfig;
	private final boolean overrideVirtualBoxAddress;
	private final ScheduledExecutorService renewPool;
	
	private static final String LOCALHOST_ADDRESS = "127.0.0.1";
	private static final String PRODUCTION_DB_CONSUL_KEY = "config/mongodb/database/si2";
	public static final String INTEGRATION_DB_CONSUL_KEY = "config/mongodb/database/integration";

	public ConnectionProvider(ConsulConfiguration consulConfig, VaultCredentials vaultCreds, boolean overrideVirtualBoxAddress) throws VaultException {
		LOGGER.debug("Connecting to Consul @ {}:{} based on config",consulConfig.ip(), consulConfig.port());
		this.overrideVirtualBoxAddress = overrideVirtualBoxAddress;
		this.renewPool = Executors.newSingleThreadScheduledExecutor();
		this.consul = new ConsulClient(consulConfig);
		this.consulConfig = consulConfig;
		this.vault = getVaultClient(vaultCreds);
		this.vaultWA = new VaultWorkaround(this.vault);
	}
	
	public ConsulClient getConsulClient() {
		return new ConsulClient(consulConfig);
	}
	
	public ConnectionFactory getRabbitMQConnectionFactory(RabbitMqRole role) throws IOException, TimeoutException, VaultException {
		ServiceHealth rabbitmqService = consul.getFirstHealtyInstance(ConfiguredService.rabbitmq);
		
		String serverAddress = overrideVirtualBoxNatAddress(rabbitmqService.getNode().getAddress());
		int serverPort = rabbitmqService.getService().getPort();

		LogicalResponse rabbitCreds = vaultWA.readRabbitMqCredentials(role.toString());

		String lease = rabbitCreds.getLeaseId();
		long leaseDuration = TimeUnit.SECONDS.convert(15, TimeUnit.MINUTES);

		renewPool.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try {
					vaultWA.renewLease(lease, leaseDuration);
				} catch (VaultException e) {
					LOGGER.warn("Failed to renewdrabbitmq lease: {}", lease, e);
				}
			}
		}, 1, 5, TimeUnit.MINUTES);
		
		Map<String, String> creds = rabbitCreds.getData();
		String rabbitMqVhost = consul.getKvAsString("config/rabbitmq/vhost");
		
		ConnectionFactory connFactory = new ConnectionFactory();
		connFactory.setUsername(creds.get("username"));
		connFactory.setPassword(creds.get("password"));
		connFactory.setHost(serverAddress);
		connFactory.setPort(serverPort);
		connFactory.setVirtualHost(rabbitMqVhost);

		LOGGER.info("Connecting to Rabbitmq server {}:{}", serverAddress, serverPort);

		return connFactory;
	}
	
	public Vault getVaultClient(VaultCredentials vaultCreds) throws VaultException {
		Service vaultSerivce = consul.getFirstHealtyInstance(ConfiguredService.vault).getService();
		Node vaultNode = consul.getFirstHealtyInstance(ConfiguredService.vault).getNode();
		
		
		String vaultAddress = "http://" + overrideVirtualBoxNatAddress(vaultNode.getAddress()) + ":" + vaultSerivce.getPort();
		LOGGER.debug("Created Vault config with address {}", vaultAddress);
		
		VaultConfig vc = new VaultConfig().address(vaultAddress).engineVersion(1).build();
		
		Vault vaultClient = new Vault(vc);
		AuthResponse auth = vaultClient.auth().loginByAppRole(vaultCreds.approleId(), vaultCreds.secretId());
		vc.token(auth.getAuthClientToken());

		long leaseDuration = vaultClient.auth().renewSelf().getAuthLeaseDuration();
		long renewInterval = leaseDuration / 2;

		LOGGER.info("Token lease duration is {} seconds, setting refresh interval at {} seconds", leaseDuration,
				renewInterval);

		renewPool.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try {
					vaultClient.auth().renewSelf();
					LOGGER.debug("Successfully renewed Vault token");
				} catch (VaultException e) {
					LOGGER.warn("Failed to renew Vault token: ", e);
				}
			}
		}, renewInterval, renewInterval, TimeUnit.SECONDS);
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					LOGGER.info("Revoking vault token...");
					vaultClient.auth().revokeSelf();
				} catch (VaultException e) {
					LOGGER.error("Failed to revoke token");
				}
			}
		}));
		
		return vaultClient;
	}
	
	protected String overrideVirtualBoxNatAddress(String originalAddress) {
		return ConnectionProvider.overrideVirtualBoxNatAddress(originalAddress, this.overrideVirtualBoxAddress);
	}
	// TODO Change to 'forceLocalhost'
	protected static String overrideVirtualBoxNatAddress(String originalAddress, boolean overrideVirtualBoxAddress) {
		if (overrideVirtualBoxAddress) {
			LOGGER.debug("Rewrote VirtualBox NAT address {} to {}", originalAddress, LOCALHOST_ADDRESS);
			return LOCALHOST_ADDRESS;
		} else {
			return originalAddress;
		}
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
		String mongoDBserverAddress = overrideVirtualBoxNatAddress(mongodbService.getNode().getAddress());

		MorphiumConfig cfg = new MorphiumConfig();
		LOGGER.info("Conneting to mongodb database {} on {}", database, mongoDBserverAddress);
		cfg.setDatabase(database);
		cfg.addHostToSeed(mongoDBserverAddress, mongodbService.getService().getPort());
		//TODO use replica sets at some point, disabled to prevent exception spam
		cfg.setReplicasetMonitoring(false);
				
		return new Morphium(cfg);
	}
	
	public MongoDbMapper getMongoDbMapper(String databaseNameConsulKey) {
		Morphium morphium = getMorphiumClient(databaseNameConsulKey);
		return new MongoDbMapper(morphium);
	}

	public MinioClient getMinioClient() throws VaultException {
		Service minioSerivce = consul.getFirstHealtyInstance(ConfiguredService.minio).getService();
		// FIXME generate minio creds for service, should not use admin account
		LogicalResponse response = this.vault.logical().read("kv/minio/admin");
		
		if (response.getRestResponse().getStatus() != 200) {
			LOGGER.error("Failed to get minio credentials due to unexpected error code {} with message {}", response.getRestResponse().getStatus(),
					new String(response.getRestResponse().getBody()));
		}

		String username = response.getData().get("username");
		String password = response.getData().get("password");

		return MinioClient.builder().endpoint("http://" + overrideVirtualBoxNatAddress(minioSerivce.getAddress()) + ":" + minioSerivce.getPort())
				.credentials(username, password)
				.build();
	}

	public void shutdown() {
		renewPool.shutdown();
	}
}
