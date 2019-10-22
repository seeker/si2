package com.github.seeker.configuration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;

public class ConfigurationBuilderTest {
	private ConfigurationBuilder cut;
	private ConsulConfiguration consulConfig;
	private static final Path TEST_CONFIG = Paths.get("unittest.yaml"); 
	
	@Before
	public void setUp() throws Exception {
		cut = new ConfigurationBuilder(TEST_CONFIG);
		consulConfig = cut.getConsulConfiguration();
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
	
}
