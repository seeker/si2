/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;

public class VaultIntegrationCredentialsTest {
	private VaultIntegrationCredentials cut;
	
	private static final String DBNODE_ROLE = "dbnode";
	
	@BeforeEach
	public void setUp() throws Exception {
		cut = new VaultIntegrationCredentials(Approle.dbnode);
	}
	
	@Test
	public void approleIsSetToEnumString() throws Exception {
		assertThat(cut.approleId(), is(DBNODE_ROLE));
	}
	
	@Test
	public void secretIdIsSetToEnumString() throws Exception {
		assertThat(cut.secretId(), is(DBNODE_ROLE));
	}
}
