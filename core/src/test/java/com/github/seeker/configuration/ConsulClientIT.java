package com.github.seeker.configuration;

import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;

import org.junit.BeforeClass;
import org.junit.Test;

import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.orbitz.consul.model.health.ServiceHealth;

public class ConsulClientIT {
	private static ConsulClient client;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ConsulConfiguration config = new ConfigurationBuilder().getConsulConfiguration();
		client = new ConsulClient(config);
	}

	@Test
	public void getFirstHealtyInstanceForMongoDb() throws Exception {
		ServiceHealth service = client.getFirstHealtyInstance(ConfiguredService.mongodb);
		
		assertThat(service.getNode().getAddress(), is("192.168.42.11"));
	}

	@Test
	public void testGetKvAsString() throws Exception {
		assertThat(client.getKvAsString("config/mongodb/database/integration"), is("mongodbintegration"));
	}
}
