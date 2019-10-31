/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.messaging;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

public class HashMessageBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(HashMessageBuilder.class);
	private static final String DEFAULT_EXCHANGE = "";
	
	private final Channel channel;
	private final QueueConfiguration queueConfig;

	private StringBuffer addedHashAlgorithms;
	private ByteArrayDataOutput messageBody;

	private Map<String, Integer> digestLengthCache;
	
	public HashMessageBuilder(Channel channel, QueueConfiguration queueConfig) {
		this.channel = channel;
		this.queueConfig = queueConfig;

		digestLengthCache = new HashMap<String, Integer>();
		
		reset();
	}
	
	private void reset() {
		addedHashAlgorithms = new StringBuffer();
		messageBody = ByteStreams.newDataOutput();
	}

	private int getDigestLength(String algorithm) throws NoSuchAlgorithmException {
		if(!digestLengthCache.containsKey(algorithm)) {
			LOGGER.debug("No digest length for algorithm {} found.", algorithm);
			loadDigestLength(algorithm);
		}
		
		return digestLengthCache.get(algorithm);
	}
	
	private void loadDigestLength(String algorithm) throws NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance(algorithm);
		
		digestLengthCache.put(algorithm, digest.getDigestLength());
		LOGGER.debug("Added digest length {} for algorithm {} to cache", digest.getDigestLength(), algorithm);
	}
	
	public HashMessageBuilder addHash(String algorithm, byte[] digest) throws NoSuchAlgorithmException {
		addedHashAlgorithms.append(Objects.requireNonNull(algorithm, "Algorithm cannot be null!"));
		addedHashAlgorithms.append(",");
		
		if(getDigestLength(algorithm) != Objects.requireNonNull(digest, "The digest cannot be null!").length) {
			throw new InvalidParameterException("Unexpected digest length for algorithm!");
		}
		
		messageBody.write(digest);
		
		return this;
	}
	
	/**
	 * Sends the hash message and resets the builder
	 * @throws IOException if there is an error sending the message
	 */
	public void send(Map<String, Object> originalMessageHeaders) throws IOException {
		Map<String, Object> hashHeaders = new HashMap<String, Object>();
		hashHeaders.put(MessageHeaderKeys.ANCHOR, originalMessageHeaders.get(MessageHeaderKeys.ANCHOR));
		hashHeaders.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, originalMessageHeaders.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH));
		hashHeaders.put(MessageHeaderKeys.HASH_ALGORITHMS, addedHashAlgorithms.toString());
		
		AMQP.BasicProperties hashProps = new AMQP.BasicProperties.Builder().headers(hashHeaders).build();
		channel.basicPublish(DEFAULT_EXCHANGE, queueConfig.getQueueName(ConfiguredQueues.hashes), hashProps, messageBody.toByteArray());
		
		reset();
	}
	
	/**
	 * Get the current buffered header.
	 * @return the current header
	 */
	public String getHashHeader() {
		return addedHashAlgorithms.toString();
	}
	
	/**
	 * Returns the current buffered message body, containing concatenated digests.
	 * @return an array containing digest(s)
	 */
	public byte[] getMessageBody() {
		return messageBody.toByteArray();
	}
}
