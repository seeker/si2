/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

import static org.junit.Assert.assertThat;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class AnchorParserTest {
	private AnchorParser cut;
	
	@Before
	public void setUp() throws Exception {
		cut = new AnchorParser();
	}

	@Test(expected=IllegalArgumentException.class)
	public void parseNull() throws Exception {
		cut.parse(null);
	}
	
	@Test
	public void parseNotAKeyValuePair() throws Exception {
		Map<String, String> map = cut.parse("foobar");
		
		assertThat(map.isEmpty(), is(true));
	}
	
	@Test
	public void singleKv() throws Exception {
		Map<String, String> map = cut.parse("foo=/mnt/baz");
		
		assertThat(map, hasEntry("foo", "/mnt/baz"));
	}
	
	@Test
	public void multipleKv() throws Exception {
		Map<String, String> map = cut.parse("foo=/mnt/baz;bar=/mnt/boo;baz=/mnt/tmp");
		
		assertThat(map, hasEntry("foo", "/mnt/baz"));
		assertThat(map, hasEntry("bar", "/mnt/boo"));
		assertThat(map, hasEntry("baz", "/mnt/tmp"));

		assertThat(map.size(), is(3));
	}
	
	@Test
	public void multipleKvWithIncorrectPair() throws Exception {
		Map<String, String> map = cut.parse("foo=/mnt/baz;bar=/mnt/boo=ding;baz=/mnt/tmp");
		
		assertThat(map, hasEntry("foo", "/mnt/baz"));
		assertThat(map, hasEntry("baz", "/mnt/tmp"));
		
		assertThat(map.size(), is(2));
	}
}
