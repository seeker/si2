/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.jupiter.api.Test;

public class ConnectionProviderTest {
	private static final String VIRTUALBOX_NAT_ADDRESS = "10.0.2.15";
	private static final String NORMAL_ADDRESS = "192.168.0.4";
	private static final String LOCALHOST_ADDRESS = "127.0.0.1";
	
	@Test
	public void vitualboxNatAddressIsReplacedWithLocalHost() throws Exception {
		assertThat(ConnectionProvider.overrideVirtualBoxNatAddress(VIRTUALBOX_NAT_ADDRESS, true), is(LOCALHOST_ADDRESS));
	}
	
	@Test
	public void nonNatAddressIsModified() throws Exception {
		assertThat(ConnectionProvider.overrideVirtualBoxNatAddress(NORMAL_ADDRESS, true), is(LOCALHOST_ADDRESS));
	}
	
	@Test
	public void doNotReplaceVitualboxNatAddress() throws Exception {
		assertThat(ConnectionProvider.overrideVirtualBoxNatAddress(VIRTUALBOX_NAT_ADDRESS, false), is(VIRTUALBOX_NAT_ADDRESS));
	}
}
