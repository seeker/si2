/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.messaging;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;


import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;

public class HashMessageHelperIT {

	private static final String ANCHOR = "anchorman";
	private static final Path RELATIVE_ANCHOR_PATH = Paths.get("foo/bar/baz/boo.jpg");
	private static final byte[] SHA256 = {-29, -80, -60, 66, -104, -4, 28, 20, -102, -5, -12, -56, -103, 111, -71, 36, 39, -82, 65, -28, 100, -101, -109, 76, -92, -107, -103, 27, 120, 82, -72, 85};
	private static final long PHASH = 348759L;
	
	private static ConnectionProvider connectionProvider;
	
	private HashMessageHelper cut;
	private Channel channelForTests;
	private Connection rabbitConn;
	
	private Map<String, Object> headers;
	private HashMessage response;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ConsulConfiguration consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		connectionProvider = new ConnectionProvider(consulConfig);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		rabbitConn = connectionProvider.getRabbitMQConnection();
		ConsulClient consul = connectionProvider.getConsulClient();
		
		Channel channel = rabbitConn.createChannel();
		channelForTests = rabbitConn.createChannel();
		
		QueueConfiguration queueConfig = new QueueConfiguration(channel, consul, true);
		assertThat(queueConfig.isIntegrationConfig(), is(true));
		
		cut = new HashMessageHelper(channel, queueConfig);
		
		headers = createTestHeaders();
		cut.sendMessage(headers, SHA256, PHASH);

		GetResponse responseMessage = channelForTests.basicGet(queueConfig.getQueueName(ConfiguredQueues.hashes), true);
		response = cut.decodeHashMessage(responseMessage.getProps().getHeaders(), responseMessage.getBody());
	}
	
	private Map<String, Object> createTestHeaders() {
		Map<String, Object> newHeaders = new HashMap<String, Object>();
		
		newHeaders.put("anchor", ANCHOR);
		newHeaders.put("path", RELATIVE_ANCHOR_PATH.toString());
		
		return newHeaders;
	}

	@After
	public void tearDown() throws Exception {
		rabbitConn.close();
	}
	
	@Test
	public void checkReceivedAnchor() throws Exception {
		assertThat(response.getAnchor(), is(ANCHOR));
	}
	
	@Test
	public void checkReceivedRelativePath() throws Exception {
		assertThat(response.getReltaivePath(), is(RELATIVE_ANCHOR_PATH.toString()));
	}
	
	@Test
	public void checkReceivedPhash() throws Exception {
		assertThat(response.getPhash(), is(PHASH));
	}
	
	@Test
	public void checkReceivedSha256() throws Exception {
		assertThat(response.getSha256(), is(SHA256));
	}
}
