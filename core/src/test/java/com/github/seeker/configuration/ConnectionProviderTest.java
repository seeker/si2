/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import org.junit.Test;

public class ConnectionProviderTest {
	private static final String VIRTUALBOX_NAT_ADDRESS = "10.0.2.15";
	private static final String NORMAL_ADDRESS = "192.168.0.4";
	private static final String LOCALHOST_ADDRESS = "127.0.0.1";

	@Test
	public void vitualboxNatAddressIsReplacedWithLocalHost() throws Exception {
		assertThat(ConnectionProvider.overrideVirtualBoxNatAddress(VIRTUALBOX_NAT_ADDRESS), is(LOCALHOST_ADDRESS));
	}
	
	@Test
	public void nonNatAddressIsNotModified() throws Exception {
		assertThat(ConnectionProvider.overrideVirtualBoxNatAddress(NORMAL_ADDRESS), is(NORMAL_ADDRESS));
	}
}
