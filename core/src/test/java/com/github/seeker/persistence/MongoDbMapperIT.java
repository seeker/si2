/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.VaultIntegrationCredentials;
import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;
import com.github.seeker.persistence.document.Hash;
import com.github.seeker.persistence.document.ImageMetaData;
import com.github.seeker.persistence.document.Thumbnail;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.MorphiumIterator;

public class MongoDbMapperIT {
	private static final Logger LOGGER = LoggerFactory.getLogger(MongoDbMapperIT.class);
	
	private static final UUID THUMBNAIL_ID = UUID.randomUUID();
	private static final UUID THUMBNAIL_ID_NEW = UUID.randomUUID();
	
	private static final String FIELD_NAME_ANCHOR = "anchor";
	private static final String FIELD_NAME_PATH = "path";
	
	
	private static final String TEST_ANCHOR = "imAnAnchor";
	private static final String TEST_ANCHOR_FRUIT = "fruit";
	private static final String TEST_ANCHOR_ANIMAL = "animal";
	
	private static final String HASH_NAME_SHA256 = "sha256";
	private static final String HASH_NAME_SHA512 = "sha512";
	private static final String HASH_NAME_PHASH = "phash";
	
	private static final Path TEST_PATH = Paths.get("foo/bar/baz.jpg");
	private static final Path TEST_PATH_NEW = Paths.get("foo/boo.jpg");

	private static final Path TEST_PATH_APPLE = Paths.get("red/apple.jpg");
	private static final Path TEST_PATH_BANANA = Paths.get("yellow/banana.png");

	private static final Path TEST_PATH_CAT = Paths.get("four/cat.jpg");
	private static final Path TEST_PATH_DOG = Paths.get("four/dog.png");
	private static final Path TEST_PATH_BIRD = Paths.get("two/bird.jpg");
	
	private static final byte[] HASH_DATA_SHA256 = new byte[]{1,2,3,5};
	
	private static MongoDbMapper mapper;

	private static Morphium morphium;
	
	private ImageMetaData metadataExisting;
	private ImageMetaData metadataNew;
	private Thumbnail thumbnailExisting;
	private Thumbnail thumbnailNew;
	
	private Map<Path, ImageMetaData> metaInstances;
	
	@Rule
	public Timeout testCaseTimeout = new Timeout((int)TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS));
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		ConfigurationBuilder configBuilder = new ConfigurationBuilder();
		ConsulConfiguration consulConfiguration = configBuilder.getConsulConfiguration();
		ConnectionProvider connectionProvider = new ConnectionProvider(consulConfiguration, new VaultIntegrationCredentials(Approle.integration), consulConfiguration.overrideVirtualBoxAddress());
				
		morphium = connectionProvider.getMorphiumClient(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);
		mapper = connectionProvider.getMongoDbMapper(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);
	}
	
	@Before
	public void setUp() throws Exception {
		Map<String, Hash> hashes = new HashMap<>();
		hashes.put(HASH_NAME_SHA256, new Hash(HASH_DATA_SHA256));
		hashes.put(HASH_NAME_PHASH, new Hash(new byte[]{6,37,3,1,5,85,2}));
		
		metadataExisting = new ImageMetaData();
		metadataExisting.setThumbnailId(new Thumbnail(123, THUMBNAIL_ID));
		metadataExisting.setHashes(hashes);
		metadataExisting.setAnchor(TEST_ANCHOR);
		metadataExisting.setPath(TEST_PATH.toString());
		
		metadataNew = new ImageMetaData();
		metadataNew.setThumbnailId(new Thumbnail(321, THUMBNAIL_ID_NEW));

		
		setUpTestData();
		
		morphium.store(metadataExisting);
		morphium.clearCachefor(ImageMetaData.class);
		morphium.clearCachefor(Thumbnail.class);
	}
	
	private void setUpTestData() {
		metaInstances = new HashMap<Path, ImageMetaData>();
		
		morphium.store(createNewMetadata(TEST_ANCHOR_FRUIT, TEST_PATH_APPLE));
		morphium.store(createNewMetadata(TEST_ANCHOR_FRUIT, TEST_PATH_BANANA));
		morphium.store(createNewMetadata(TEST_ANCHOR_ANIMAL, TEST_PATH_CAT));
		morphium.store(createNewMetadata(TEST_ANCHOR_ANIMAL, TEST_PATH_DOG));
		morphium.store(createNewMetadata(TEST_ANCHOR_ANIMAL, TEST_PATH_BIRD));
	}
	
	private ImageMetaData createNewMetadata(String Anchor, Path path) {
		ImageMetaData meta = new ImageMetaData();
		
		meta.setAnchor(Anchor);
		meta.setPath(path.toString());
		
		metaInstances.put(path, meta);
		
		return meta;
	}

	@After
	public void tearDown() {
		cleanUpCollection(ImageMetaData.class);
		morphium.clearCachefor(ImageMetaData.class);
		
		assertThat(mapper.getImageMetadataCount(), is(0L));
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
	public void queryForNonExistingMetadata() throws Exception {
		assertThat(mapper.hasHash(TEST_ANCHOR, TEST_PATH_NEW, HASH_NAME_SHA256), is(false));
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
	
	@Test
	public void queryForHashByHashVAlue() throws Exception {
		List<ImageMetaData> meta = mapper.getMetadataByHash(HASH_NAME_SHA256, HASH_DATA_SHA256);
		
		assertThat(meta.size(), is(1));
		assertThat(meta.get(0).getPath(), is(metadataExisting.getPath()));
	}
	
	@Test
	public void queryForMetadataWithAnchor() throws Exception {
		morphium.store(metadataNew);
		
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("anchor", TEST_ANCHOR);
		
		List<ImageMetaData> result = mapper.getImageMetadata(params);
		
		assertThat(result.get(0).getAnchor(), is(metadataExisting.getAnchor()));
		assertThat(result.get(0).getPath(), is(metadataExisting.getPath()));
		assertThat(result.size(), is(1));
	}

	@Test
	public void queryForMetadataWithInvalidAnchor() throws Exception {
		morphium.store(metadataNew);
		
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("anchor", "echo");
		
		List<ImageMetaData> result = mapper.getImageMetadata(params);
		
		assertThat(result.size(), is(0));
	}
	
	@Test
	public void filterMetadataByAnchor() throws Exception {
		Map<String, Object> filterParameters = new HashMap<String, Object>();
		
		filterParameters.put(FIELD_NAME_ANCHOR, TEST_ANCHOR_FRUIT);
		
		List<ImageMetaData> result = mapper.getImageMetadata(filterParameters);
		
		assertThat(result, is(containsInAnyOrder(
				metaInstances.get(TEST_PATH_BANANA),
				metaInstances.get(TEST_PATH_APPLE)
				)));
	}

	@Test
	public void filterMetadataByAnchorCount() throws Exception {
		Map<String, Object> filterParameters = new HashMap<String, Object>();
		
		filterParameters.put(FIELD_NAME_ANCHOR, TEST_ANCHOR_ANIMAL);
		
		long result = mapper.getFilteredImageMetadataCount(filterParameters);
		
		assertThat(result, is(3L));
	}

	@Test
	public void filterMetadataByPath() throws Exception {
		Map<String, Object> filterParameters = new HashMap<String, Object>();
		
		filterParameters.put(FIELD_NAME_PATH, "four");
		
		List<ImageMetaData> result = mapper.getImageMetadata(filterParameters);
		
		assertThat(result, is(containsInAnyOrder(
				metaInstances.get(TEST_PATH_CAT),
				metaInstances.get(TEST_PATH_DOG)
				)));
	}

	@Test
	public void filterMetadataByPathAndReturnFirst() throws Exception {
		Map<String, Object> filterParameters = new HashMap<String, Object>();
		
		filterParameters.put(FIELD_NAME_PATH, "four");
		
		List<ImageMetaData> result = mapper.getImageMetadata(filterParameters,0,1);
		
		assertThat(result, is(containsInAnyOrder(
				metaInstances.get(TEST_PATH_CAT)
				)));
	}

	@Test
	public void filterMetadataByPathAndReturnLast() throws Exception {
		Map<String, Object> filterParameters = new HashMap<String, Object>();
		
		filterParameters.put(FIELD_NAME_PATH, "four");
		
		List<ImageMetaData> result = mapper.getImageMetadata(filterParameters,1,1);
		
		assertThat(result, is(containsInAnyOrder(
				metaInstances.get(TEST_PATH_DOG)
				)));
	}

	@Test
	public void filterMetadataBlankReturnsAll() throws Exception {
		Map<String, Object> filterParameters = new HashMap<String, Object>();
		
		List<ImageMetaData> result = mapper.getImageMetadata(filterParameters);
		
		assertThat(result, is(containsInAnyOrder(
				metaInstances.get(TEST_PATH_CAT),
				metaInstances.get(TEST_PATH_DOG),
				metaInstances.get(TEST_PATH_BIRD),
				metaInstances.get(TEST_PATH_BANANA),
				metaInstances.get(TEST_PATH_APPLE),
				metadataExisting
				)));
	}
	
	@Test
	public void imageIdExists() throws Exception {
		assertThat(mapper.hasImageId(THUMBNAIL_ID.toString()), is(true));
	}
	
	@Test
	public void imageIdDoesNotExist() throws Exception {
		assertThat(mapper.hasImageId(THUMBNAIL_ID_NEW.toString()), is(false));
	}

	private void cleanUpCollection(Class<? extends Object> clazz) {
		morphium.dropCollection(clazz);
		morphium.clearCachefor(clazz);
	}

	private long now() {
		return Calendar.getInstance().getTimeInMillis();
	}

	@Test
	public void getThumbnailsToResizeNoMatch() {
		MorphiumIterator<ImageMetaData> iter = mapper.getThumbnailsToResize(333);

		assertThat(iter.getCount(), is(6L));
	}

	@Test
	public void getThumbnailsToResizeWithMatch() {
		MorphiumIterator<ImageMetaData> iter = mapper.getThumbnailsToResize(123);

		assertThat(iter.getCount(), is(5L));
	}
}
