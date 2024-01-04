package com.github.seeker.configuration;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.orbitz.consul.model.health.ServiceHealth;

public class ConsulClientIT {
	private static ConsulClient client;
	
	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
		ConsulConfiguration config = new ConfigurationBuilder().getConsulConfiguration();
		client = new ConsulClient(config);
	}

	@Test
	public void getFirstHealtyInstanceForMongoDb() throws Exception {
		ServiceHealth service = client.getFirstHealtyInstance(ConfiguredService.mongodb);
		
		assertThat(service.getService().getTags(), hasItem("mongodb"));
	}

	@Test
	public void testGetKvAsString() throws Exception {
		assertThat(client.getKvAsString("config/mongodb/database/integration"), is("mongodbintegration"));
	}
}
