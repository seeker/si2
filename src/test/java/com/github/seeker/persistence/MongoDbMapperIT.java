/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import static org.junit.Assert.*;

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
import com.github.seeker.persistence.document.Thumbnail;
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
	private static final byte[] IMAGE_DATA_NEW = { 7, 3, 22, 48, 33, 87 };

	private static MongoDbMapper mapper;

	private static MorphiumConfig cfg;
	private static Morphium morphium;
	private static Consul client;
	
	private ImageMetaData metadataExisting;
	private ImageMetaData metadataNew;
	private Thumbnail thumbnailExisting;
	private Thumbnail thumbnailNew;
	
	@BeforeClass
	public static void setUpClass() {
		ConsulConfiguration consulConfiguration = new ConfigurationBuilder().getConsulConfiguration();

		client = Consul.builder().withHostAndPort(HostAndPort.fromParts(consulConfiguration.ip(), consulConfiguration.port())).build();
		
		HealthClient healthClient = client.healthClient();
		List<ServiceHealth> healtyMongoDbServers = healthClient.getHealthyServiceInstances("mongodb",QueryOptions.blockSeconds(5, "foo").datacenter("vagrant").build()).getResponse();
		
		String database = client.keyValueClient().getValue("config/mongodb/database/integration").get().getValueAsString().get();
		String serverAddress = healtyMongoDbServers.get(0).getNode().getAddress();
		
		cfg = new MorphiumConfig();
		LOGGER.info("Conneting to mongodb database {}", database);
		cfg.setDatabase(database);
		cfg.addHostToSeed(serverAddress);
				
		morphium = new Morphium(cfg);
		mapper = new MongoDbMapper(morphium);
	}
	
	@Before
	public void setUp() throws Exception {
		thumbnailExisting = new Thumbnail(42, IMAGE_DATA);
		metadataExisting = new ImageMetaData();
		metadataExisting.setThumbnail(thumbnailExisting);
		
		thumbnailNew = new Thumbnail(20, IMAGE_DATA_NEW);
		metadataNew = new ImageMetaData();
		metadataNew.setThumbnail(thumbnailNew);
		
		Morphium dbClient = new Morphium(cfg);
		dbClient.store(metadataExisting);
		dbClient.close();
	}

	@After
	public void tearDown() {
		cleanUpCollection(ImageMetaData.class);
	}
	
	@Test
	public void insertDocument() {
		mapper.storeDocument(metadataNew);
	}
	
	private void cleanUpCollection(Class<? extends Object> clazz) {
		morphium.dropCollection(clazz);
		morphium.clearCachefor(clazz);
	}

	private long now() {
		return Calendar.getInstance().getTimeInMillis();
	}
}
