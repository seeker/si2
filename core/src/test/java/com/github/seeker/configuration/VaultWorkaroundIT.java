/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.junit.Assert.assertThat;

import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.bettercloud.vault.response.LogicalResponse;
import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;

public class VaultWorkaroundIT {
	private static ConsulConfiguration consulConfig;
	private static ConnectionProvider connProv;

	private VaultWorkaround cut;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		connProv = new ConnectionProvider(consulConfig, new VaultIntegrationCredentials(Approle.integration), consulConfig.overrideVirtualBoxAddress());
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		cut = new VaultWorkaround(connProv.getVaultClient(new VaultIntegrationCredentials(Approle.integration)));
	}

	@After
	public void tearDown() throws Exception {

	}

	@Test
	public void getRabbitMQCredentials() throws Exception {
		LogicalResponse response = cut.readRabbitMqCredentials("dbnode");

		Map<String, String> creds = response.getData();

		assertThat(creds, hasKey("username"));
		assertThat(creds, hasKey("password"));
	}
}
