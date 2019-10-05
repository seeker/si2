/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.messaging;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;

public class HashMessageHelperIT extends MessageITBase {
	private static final String ANCHOR = "anchorman";
	private static final Path RELATIVE_ANCHOR_PATH = Paths.get("foo/bar/baz/boo.jpg");
	private static final byte[] SHA256 = {-29, -80, -60, 66, -104, -4, 28, 20, -102, -5, -12, -56, -103, 111, -71, 36, 39, -82, 65, -28, 100, -101, -109, 76, -92, -107, -103, 27, 120, 82, -72, 85};
	private static final long PHASH = 348759L;
	
	private HashMessageHelper cut;
	private Channel channelForTests;
	
	private Map<String, Object> headers;
	private HashMessage message;
	
	@Before
	public void setUp() throws Exception {
		channelForTests = rabbitConn.createChannel();
		cut = new HashMessageHelper(channel, queueConfig);
		
		headers = createTestHeaders();
		cut.sendMessage(headers, SHA256, PHASH);
		
		message = waitForMessage(); 
	}

	/**
	 * Keeps polling until a message is received or the timeout is reached.
	 * @return a received message
	 * @throws IOException
	 * @throws InterruptedException if the call is interrupted while waiting for a message
	 * @throws TimeoutException if the timeout is reached
	 */
	private HashMessage waitForMessage() throws IOException, InterruptedException, TimeoutException {
		GetResponse responseMessage;
		int abortCount = 60;
		
		do {
			responseMessage = channelForTests.basicGet(queueConfig.getQueueName(ConfiguredQueues.hashes), false);
			Thread.sleep(50);
			abortCount--;
			
			if (abortCount == 0) {
				throw new TimeoutException("Did not get a message in time");
			}
		} while(responseMessage == null);
		
		return cut.decodeHashMessage(responseMessage.getProps().getHeaders(), responseMessage.getBody());
		
	}
	
	private Map<String, Object> createTestHeaders() {
		Map<String, Object> newHeaders = new HashMap<String, Object>();
		
		newHeaders.put(MessageHeaderKeys.ANCHOR, ANCHOR);
		newHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, RELATIVE_ANCHOR_PATH.toString());
		
		return newHeaders;
	}
	

	@After
	public void tearDown() throws Exception {
		for(ConfiguredQueues queue : ConfiguredQueues.values()) {
			channelForTests.queueDelete(queueConfig.getQueueName(queue));
		}
	}
	
	@Test
	public void checkReceivedAnchor() throws Exception {
		assertThat(message.getAnchor(), is(ANCHOR));
	}
	
	@Test
	public void checkReceivedRelativePath() throws Exception {
		assertThat(message.getReltaivePath(), is(RELATIVE_ANCHOR_PATH.toString()));
	}
	
	@Test
	public void checkReceivedPhash() throws Exception {
		assertThat(message.getPhash(), is(PHASH));
	}
	
	@Test
	public void checkReceivedSha256() throws Exception {
		assertThat(message.getSha256(), is(SHA256));
	}
}
