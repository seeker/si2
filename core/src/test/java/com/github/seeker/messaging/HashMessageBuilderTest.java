/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.messaging;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.github.seeker.configuration.QueueConfiguration;
import com.rabbitmq.client.Channel;


public class HashMessageBuilderTest {
	private static final int SHA256_NUMBER_OF_BYTES = 32;
	private static final int SHA512_NUMBER_OF_BYTES = 64;
	
	private static final String SHA256_ALGORITHM_NAME = "SHA-256";
	private static final String SHA512_ALGORITHM_NAME = "SHA-512";
	private static final String MD5_ALGORITHM_NAME = "MD5";
	private static final String INVALID_ALGORITHM_NAME = "STOOGE";
	
	private static byte[] QUICK_FOX_SHA256;
	private static byte[] QUICK_FOX_SHA512;
	private static byte[] QUICK_FOX_MD5;
	
	private HashMessageBuilder cut;

	@Rule public ExpectedException thrown = ExpectedException.none();
	
	@Mock
	private Channel channel;
	
	@Mock
	private QueueConfiguration queueConfig;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		byte[] text = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.US_ASCII);
		
		QUICK_FOX_SHA256 = MessageDigest.getInstance(SHA256_ALGORITHM_NAME).digest(text);
		QUICK_FOX_SHA512 = MessageDigest.getInstance(SHA512_ALGORITHM_NAME).digest(text);
		QUICK_FOX_MD5 = MessageDigest.getInstance(MD5_ALGORITHM_NAME).digest(text);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);

		cut = new HashMessageBuilder(channel, queueConfig);
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public void hashHeaderIsEmpty() throws Exception {
		assertThat(cut.getHashHeader(), is(emptyString()));
	}
	
	@Test
	public void digestBodyIsEmpty() throws Exception {
		assertThat(cut.getMessageBody(), is(new byte[] {}));
	}
	
	@Test
	public void validAlgorithmIsOk() throws Exception {
		cut.addHash(SHA256_ALGORITHM_NAME, new byte[SHA256_NUMBER_OF_BYTES]);
	}
	
	@Test(expected=NoSuchAlgorithmException.class)
	public void inValidAlgorithmThrowsException() throws Exception {
		cut.addHash(INVALID_ALGORITHM_NAME, new byte[SHA256_NUMBER_OF_BYTES]);
	}
	
	@Test(expected=InvalidParameterException.class)
	public void incorrectDigestLengthThrowsException() throws Exception {
		cut.addHash(SHA256_ALGORITHM_NAME, new byte[SHA256_NUMBER_OF_BYTES - 1]);
	}
	
	@Test
	public void bodyConcatenatedSize() throws Exception {
		byte[] body = cut.addHash(SHA256_ALGORITHM_NAME, QUICK_FOX_SHA256).addHash(SHA512_ALGORITHM_NAME, QUICK_FOX_SHA512).getMessageBody();
		
		assertThat(body.length, is(SHA256_NUMBER_OF_BYTES + SHA512_NUMBER_OF_BYTES));
	}
	
	@Test
	public void algorithmConcatenatedInHashHeader() throws Exception {
		String header = cut.addHash(SHA256_ALGORITHM_NAME, QUICK_FOX_SHA256).addHash(SHA512_ALGORITHM_NAME, QUICK_FOX_SHA512).getHashHeader();
		
		assertThat(header, is("SHA-256,SHA-512,"));
	}
	
	@Test
	public void nullValueforAlgorithmThrowsExceptionTypeNPE() throws Exception {
		thrown.expect(NullPointerException.class);	
		
		cut.addHash(null, QUICK_FOX_SHA256);
	}

	@Test
	public void nullValueforAlgorithmThrowsExceptionWithMessage() throws Exception {
		thrown.expectMessage(containsString("Algorithm cannot be null!"));
		
		cut.addHash(null, QUICK_FOX_SHA256);
	}
	
	@Test
	public void nullValueforDigestThrowsExceptionTypeNPE() throws Exception {
		thrown.expect(NullPointerException.class);	
		
		cut.addHash(SHA256_ALGORITHM_NAME, null);
	}
	
	@Test
	public void nullValueforDigestThrowsExceptionWithMessage() throws Exception {
		thrown.expectMessage(containsString("digest cannot be null!"));
		
		cut.addHash(SHA256_ALGORITHM_NAME, null);
	}
}
