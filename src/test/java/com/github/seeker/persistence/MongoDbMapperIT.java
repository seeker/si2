/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import java.util.Calendar;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.MongoDbConfiguration;
import com.github.seeker.persistence.document.ImageMetaData;

import de.caluga.morphium.Morphium;

public class MongoDbMapperIT {
	private static final byte[] IMAGE_DATA = { 1, 2, 2, 45, 6, 4 };

	private static MongoDbMapper mapper;

	private static Morphium morphium;

	private ImageMetaData metadata;
	
	@BeforeClass
	public static void setUpClass() {
		MongoDbConfiguration integrationConfig = new ConfigurationBuilder().provideMongoDbConfiguration();

		morphium = new MongoDbBuilder().build(integrationConfig);
		mapper = new MongoDbMapper(morphium);
	}

	@Before
	public void setUp() throws Exception {
		metadata = new ImageMetaData();
	}

	@After
	public void tearDown() {

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
