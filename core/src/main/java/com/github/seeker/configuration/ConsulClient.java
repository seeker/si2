package com.github.seeker.configuration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.cache.KVCache;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.option.QueryOptions;

/**
 * Load configuration via consul. This class contains boilerplate code to connect to
 * consul to get the configuration.
 */
public class ConsulClient {
	private static Logger LOGGER = LoggerFactory.getLogger(ConsulClient.class);
	
	private final Consul client;
	private final String datacenter;
	
	/**
	 * Services available for this project
	 */
	public enum ConfiguredService {
		rabbitmq, mongodb, vault, minio
	}
	
	public ConsulClient(ConsulConfiguration consulConfig) {
		this.datacenter = consulConfig.datacenter();
		client = Consul.builder().withHostAndPort(HostAndPort.fromParts(consulConfig.ip(), consulConfig.port())).withReadTimeoutMillis(20000).build();
	}
	
	public ServiceHealth getFirstHealtyInstance(ConfiguredService service) {
		HealthClient healthClient = client.healthClient();
		List<ServiceHealth> healtyInstances = healthClient.getHealthyServiceInstances(service.toString(),QueryOptions.blockSeconds(5, service.toString()).datacenter(datacenter).build()).getResponse();
		
		LOGGER.debug("Query healthy instance for {}", service);
		
		if(healtyInstances.isEmpty()) {
			LOGGER.error("No healthy instance for service {}", service);
			throw new IllegalStateException("No healty instance available for " + service.toString());
		}
		
		return healtyInstances.get(0);
	}
	
	public String getKvAsString(String key) {
		return client.keyValueClient().getValue(key).get().getValueAsString().get();
	}
	
	public long getKvAsLong(String key) {
		String value = client.keyValueClient().getValue(key).get().getValueAsString().get();
		return Long.parseLong(value); 
	}
	
	/**
	 * Return a {@link KVCache} for the given root path.
	 * Listeners must still be added to the cache and it needs to be started.
	 * 
	 * @param rootPath the root path key for changes to monitor
	 * 
	 * @return a cache configured for the path
	 */
	public KVCache getKVCache(String rootPath) {
		return KVCache.newCache(client.keyValueClient(), rootPath);
	}
}
