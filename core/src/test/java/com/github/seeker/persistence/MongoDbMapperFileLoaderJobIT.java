/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.VaultIntegrationCredentials;
import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;
import com.github.seeker.persistence.document.FileLoaderJob;

import de.caluga.morphium.Morphium;

public class MongoDbMapperFileLoaderJobIT {
	private static final String TEST_ANCHOR = "imAnAnchor";
	private static final String TEST_ANCHOR_FRUIT = "fruit";
	private static final String TEST_ANCHOR_ANIMAL = "animal";

	private static final Path TEST_PATH_APPLE = Paths.get("red/apple.jpg");
	private static final Path TEST_PATH_BANANA = Paths.get("yellow/banana.png");

	private static final Path TEST_PATH_CAT = Paths.get("four/cat.jpg");
	private static final Path TEST_PATH_DOG = Paths.get("four/dog.png");

	private static MongoDbMapper mapper;

	private static Morphium morphium;

	@Rule
	public Timeout testCaseTimeout = new Timeout((int) TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS));

	@BeforeClass
	public static void setUpClass() throws Exception {
		ConfigurationBuilder configBuilder = new ConfigurationBuilder();
		ConsulConfiguration consulConfiguration = configBuilder.getConsulConfiguration();
		ConnectionProvider connectionProvider = new ConnectionProvider(consulConfiguration,
				new VaultIntegrationCredentials(Approle.integration), consulConfiguration.overrideVirtualBoxAddress());

		morphium = connectionProvider.getMorphiumClient(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);
		mapper = connectionProvider.getMongoDbMapper(ConnectionProvider.INTEGRATION_DB_CONSUL_KEY);
	}

	@Before
	public void setUp() throws Exception {
		setUpTestData();

		morphium.clearCachefor(FileLoaderJob.class);
	}

	private void setUpTestData() {
		morphium.store(new FileLoaderJob(TEST_ANCHOR_ANIMAL, TEST_PATH_APPLE.toString()));
		morphium.store(new FileLoaderJob(TEST_ANCHOR_FRUIT, TEST_PATH_BANANA.toString()));

		FileLoaderJob completed1 = new FileLoaderJob(TEST_ANCHOR_ANIMAL, TEST_PATH_CAT.toString());
		completed1.markCompleted();

		morphium.store(completed1);
	}

	@After
	public void tearDown() {
		cleanUpCollection(FileLoaderJob.class);
		morphium.clearCachefor(FileLoaderJob.class);

		assertThat(morphium.createQueryFor(FileLoaderJob.class).countAll(), is(0L));
	}

	private void cleanUpCollection(Class<? extends Object> clazz) {
		morphium.dropCollection(clazz);
		morphium.clearCachefor(clazz);
	}

	@Test
	public void loadOpenFileLoadJobsForAnchor() throws Exception {
		FileLoaderJob job = mapper.getOpenFileLoadJobsForAnchor(TEST_ANCHOR_ANIMAL);

		assertThat(job, is(notNullValue()));
	}

	@Test
	public void openFileLoadJobForAnchorIsCorrect() throws Exception {
		FileLoaderJob job = mapper.getOpenFileLoadJobsForAnchor(TEST_ANCHOR_ANIMAL);

		assertThat(job.getAnchor(), is(TEST_ANCHOR_ANIMAL));
		assertThat(job.getRelativePath(), is(TEST_PATH_APPLE.toString()));
		assertThat(job.isCompleted(), is(false));
	}

	@Test
	public void loadAllFileLoadJobs() throws Exception {
		List<FileLoaderJob> jobs = mapper.getAllFileLoadJobs();

		assertThat(jobs.size(), is(3));
	}

	@Test
	public void storeNewFileLoadJob() throws Exception {
		mapper.storeFileLoadJob(new FileLoaderJob(TEST_ANCHOR, TEST_PATH_DOG.toString()));

		FileLoaderJob job = mapper.getOpenFileLoadJobsForAnchor(TEST_ANCHOR);
		assertThat(job, is(notNullValue()));
	}

	@Test
	public void updateExisitingJob() throws Exception {
		FileLoaderJob job = mapper.getOpenFileLoadJobsForAnchor(TEST_ANCHOR_ANIMAL);

		job.markCompleted();

		mapper.storeFileLoadJob(job);

		FileLoaderJob jobAfterUpdate = mapper.getOpenFileLoadJobsForAnchor(TEST_ANCHOR_ANIMAL);

		assertThat(jobAfterUpdate, is(nullValue()));
	}
}
