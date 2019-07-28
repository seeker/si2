/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import de.caluga.morphium.driver.MorphiumDriverException;

public class MongoDbMapperIT {
	private static final Logger LOGGER = LoggerFactory.getLogger(MongoDbMapperIT.class);
	
	private static final UUID THUMBNAIL_ID = UUID.randomUUID();
	private static final UUID THUMBNAIL_ID_NEW = UUID.randomUUID();
	
	private static final String TEST_ANCHOR = "imAnAnchor";
	private static final String HASH_NAME_SHA256 = "sha256";
	private static final String HASH_NAME_SHA512 = "sha512";
	private static final Path TEST_PATH = Paths.get("foo/bar/baz.jpg");

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
		Map<String, List<Byte>> hashes = new HashMap<>();
		hashes.put(HASH_NAME_SHA256, Arrays.asList(new Byte[]{1,2,3,5}));
		
		metadataExisting = new ImageMetaData();
		metadataExisting.setThumbnailId(THUMBNAIL_ID);
		metadataExisting.setHashes(hashes);
		metadataExisting.setAnchor(TEST_ANCHOR);
		metadataExisting.setPath(TEST_PATH.toString());
		
		metadataNew = new ImageMetaData();
		metadataNew.setThumbnailId(THUMBNAIL_ID_NEW);

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
	
	@Test
	public void hasSha256Hash() throws Exception {
		assertThat(mapper.hasHash(TEST_ANCHOR, TEST_PATH, HASH_NAME_SHA256), is(true));
	}
	
	@Test
	public void doesNotHaveSha512Hash() throws Exception {
		assertThat(mapper.hasHash(TEST_ANCHOR, TEST_PATH, HASH_NAME_SHA512), is(false));
	}
	
	@Test
	public void getExisitingMetadataIsNotNull() throws Exception {
		ImageMetaData meta = mapper.getImageMetadata(TEST_ANCHOR, TEST_PATH);
		
		assertThat(meta, is(notNullValue()));
	}
	
	@Test
	public void getNonExisitingMetadataIsNull() throws Exception {
		ImageMetaData meta = mapper.getImageMetadata("none", TEST_PATH);
		
		assertThat(meta, is(nullValue()));
	}
	
	@Test(expected=RuntimeException.class)
	public void insertDuplicateThumbnailUUID() throws Exception {
		metadataNew.setThumbnailId(THUMBNAIL_ID);
		
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
