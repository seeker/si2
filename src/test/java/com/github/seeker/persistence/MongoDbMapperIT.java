/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import java.util.Calendar;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.persistence.document.ImageMetaData;
import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.option.QueryOptions;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;

public class MongoDbMapperIT {
	private static final Logger LOGGER = LoggerFactory.getLogger(MongoDbMapperIT.class);
	
	private static final byte[] IMAGE_DATA = { 1, 2, 2, 45, 6, 4 };

	private static MongoDbMapper mapper;

	private static Morphium morphium;
	private static Consul client;
	
	private ImageMetaData metadata;
	
	@BeforeClass
	public static void setUpClass() {
		ConsulConfiguration consulConfiguration = new ConfigurationBuilder().getConsulConfiguration();

		client = Consul.builder().withHostAndPort(HostAndPort.fromParts(consulConfiguration.ip(), consulConfiguration.port())).build();
		
		HealthClient healthClient = client.healthClient();
		List<ServiceHealth> healtyMongoDbServers = healthClient.getHealthyServiceInstances("mongodb",QueryOptions.blockSeconds(5, "foo").datacenter("vagrant").build()).getResponse();
		String database = client.keyValueClient().getValue("config/mongodb/database/integration").get().getValueAsString().get();
		
		MorphiumConfig cfg = new MorphiumConfig();
		LOGGER.info("Conneting to mongodb database {}", database);
		cfg.setDatabase(database);
		cfg.addHostToSeed(healtyMongoDbServers.get(0).getNode().getAddress());
				
		morphium = new Morphium(cfg);
		mapper = new MongoDbMapper(morphium);
	}

	@Before
	public void setUp() throws Exception {
		metadata = new ImageMetaData();
	}

	@After
	public void tearDown() {
		cleanUpCollection(ImageMetaData.class);
	}
	
	@Test(timeout=3000)
	public void insertDocument() {
		mapper.storeDocument(metadata);
	}

	private void cleanUpCollection(Class<? extends Object> clazz) {
		morphium.dropCollection(clazz);
		morphium.clearCachefor(clazz);
	}

	private long now() {
		return Calendar.getInstance().getTimeInMillis();
	}
}
