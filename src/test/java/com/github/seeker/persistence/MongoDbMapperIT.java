/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.persistence.document.ImageMetaData;
import com.github.seeker.persistence.document.Thumbnail;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.health.ServiceHealth;

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
	
	@Rule
	public Timeout testCaseTimeout = new Timeout((int)TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS));
	
	@BeforeClass
	public static void setUpClass() {
		ConsulConfiguration consulConfiguration = new ConfigurationBuilder().getConsulConfiguration();

		ConsulClient consulClient = new ConsulClient(consulConfiguration);
		
		ServiceHealth mongodbService = consulClient.getFirstHealtyInstance(ConfiguredService.mongodb);
		
		String database = consulClient.getKvAsString("config/mongodb/database/integration");
		String serverAddress = mongodbService.getNode().getAddress();
		
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
		dbClient.clearCachefor(ImageMetaData.class);
		dbClient.clearCachefor(Thumbnail.class);
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
