package com.github.seeker.configuration;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.hamcrest.collection.IsMapContaining.hasValue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class ConfigurationBuilderTest {
	private static final String LOADER_ANCHOR_KEY1 = "anchorOne";
	private static final String LOADER_ANCHOR_KEY2 = "anchorTwo";
	private static final String LOADER_ANCHOR_VALUE1 = "/var/test/data";
	private static final String LOADER_ANCHOR_VALUE2 = "D:\\test\\path";
	
	private ConfigurationBuilder cut;
	private ConsulConfiguration consulConfig;
	private FileLoaderConfiguration fileLoaderConfig;
	private static final Path TEST_CONFIG = Paths.get("unittest.yaml"); 
	
	@BeforeEach
	public void setUp() throws Exception {
		cut = new ConfigurationBuilder(TEST_CONFIG);
		consulConfig = cut.getConsulConfiguration();
		fileLoaderConfig = cut.getFileLoaderConfiguration();
	}
	
	@Test
	public void consulConfigurationIsNotNull() throws Exception {
		assertThat(consulConfig, is(notNullValue()));
	}

	@Test
	public void getIP() throws Exception {
		assertThat(consulConfig.ip(), is("localhost"));
	}

	@Test
	public void getDatacenter() throws Exception {
		assertThat(consulConfig.datacenter(), is("unittest"));
	}

	@Test
	public void getPort() throws Exception {
		assertThat(consulConfig.port(), is(42));
	}
	
	@Test
	public void fileLoader1stAnchorKey() throws Exception {
		assertThat(fileLoaderConfig.anchors(),  hasKey(containsString(LOADER_ANCHOR_KEY1)));
	}
	
	@Test
	public void fileLoader2ndAnchorKey() throws Exception {
		assertThat(fileLoaderConfig.anchors(), hasKey(containsString(LOADER_ANCHOR_KEY2)));
	}
	
	@Test
	public void fileLoader1stAnchorValue() throws Exception {
		assertThat(fileLoaderConfig.anchors(),  hasValue(containsString(LOADER_ANCHOR_VALUE1)));
	}
	
	@Test
	public void fileLoader2ndAnchorValue() throws Exception {
		assertThat(fileLoaderConfig.anchors(), hasValue(containsString(LOADER_ANCHOR_VALUE2)));
	}
}
