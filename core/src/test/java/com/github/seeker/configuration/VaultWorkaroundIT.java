/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.collection.IsMapContaining.hasKey;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bettercloud.vault.response.LogicalResponse;
import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;

public class VaultWorkaroundIT {
	private static ConsulConfiguration consulConfig;
	private static ConnectionProvider connProv;

	private VaultWorkaround cut;

	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
		consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		connProv = new ConnectionProvider(consulConfig, new VaultIntegrationCredentials(Approle.integration), consulConfig.overrideVirtualBoxAddress());
	}

	@AfterAll
	public static void tearDownAfterClass() throws Exception {
	}

	@BeforeEach
	public void setUp() throws Exception {
		cut = new VaultWorkaround(connProv.getVaultClient(new VaultIntegrationCredentials(Approle.integration)));
	}

	@AfterEach
	public void tearDown() throws Exception {

	}

	@Test
	public void getRabbitMQCredentials() throws Exception {
		LogicalResponse response = cut.readRabbitMqCredentials("dbnode");

		Map<String, String> creds = response.getData();

		assertThat(creds, hasKey("username"));
		assertThat(creds, hasKey("password"));
	}

	@Test
	public void renewLease() throws Exception {
		LogicalResponse credResponse = cut.readRabbitMqCredentials("dbnode");

		String leaseId = credResponse.getLeaseId();
		LogicalResponse renewResponse = cut.renewLease(leaseId, 300);

		assertThat(renewResponse.getRestResponse().getStatus(), is(200));
	}

	@Test
	public void leaseTTLisExtended() throws Exception {
		LogicalResponse credResponse = cut.readRabbitMqCredentials("dbnode");

		String leaseId = credResponse.getLeaseId();
		LogicalResponse renewResponse = cut.renewLease(leaseId, 250);

		assertThat(renewResponse.getLeaseDuration(), is(both(greaterThan(200L)).and(lessThan(300L))));

		LogicalResponse extendResponse = cut.renewLease(leaseId, 1500);

		assertThat(extendResponse.getLeaseDuration(), is(greaterThan(1400L)));
	}
}
