/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.messaging;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

/**
 * Packs and unpacks hash messages.
 */
public class HashMessageHelper {
	private static final String DEFAULT_EXCHANGE = "";
	private static final int EXPECTED_BODY_SIZE = 40;
	private static final int SHA256_NUMBER_OF_BYTES = 32;
	
	private final Channel channel;
	private final QueueConfiguration queueConfig;

	public HashMessageHelper(Channel channel, QueueConfiguration queueConfig) {
		this.channel = channel;
		this.queueConfig = queueConfig;
	}
	
	public void sendMessage(Map<String, Object> originalMessageHeaders, byte[] sha256, long phash) throws IOException {
		Map<String, Object> hashHeaders = new HashMap<String, Object>();
		hashHeaders.put(MessageHeaderKeys.ANCHOR, originalMessageHeaders.get(MessageHeaderKeys.ANCHOR));
		hashHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, originalMessageHeaders.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));
		
		ByteArrayDataOutput data = ByteStreams.newDataOutput(EXPECTED_BODY_SIZE);
		data.writeLong(phash);
		data.write(sha256);
		
		byte[] body = data.toByteArray();
		
		AMQP.BasicProperties hashProps = new AMQP.BasicProperties.Builder().headers(hashHeaders).build();
		channel.basicPublish(DEFAULT_EXCHANGE, queueConfig.getQueueName(ConfiguredQueues.hashes), hashProps, body);
	}
	
	public HashMessage decodeHashMessage(Map<String, Object> headers, byte[] body) throws IOException {
		String anchor = headers.get(MessageHeaderKeys.ANCHOR).toString();
		String path = headers.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH).toString();

		DataInput in  = ByteStreams.newDataInput(body);
		long phash = in.readLong();
		
		byte[] sha256 = new byte[SHA256_NUMBER_OF_BYTES];
		in.readFully(sha256);
		
		return new HashMessage(anchor, path, sha256, phash);
	}
}
