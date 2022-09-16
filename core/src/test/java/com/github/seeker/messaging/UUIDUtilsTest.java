package com.github.seeker.messaging;

import static org.junit.Assert.assertThat;
import static org.hamcrest.core.Is.is;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

public class UUIDUtilsTest {
	private UUID uuid;
	
	@Before
	public void setUp() throws Exception {
		uuid = UUID.randomUUID();
	}

	@Test
	public void testUUIDtoByte() {
		byte[] byteform = UUIDUtils.UUIDtoByte(uuid);
		
		ByteBuffer buffer = ByteBuffer.wrap(byteform);
		UUID uuidFromBytes = new UUID(buffer.getLong(), buffer.getLong());
		
		assertThat(uuidFromBytes, is(uuid));
	}

	@Test
	public void testByteToUUID() {
		byte[] byteform = UUIDUtils.UUIDtoByte(uuid);
		UUID uuidFromBytes = UUIDUtils.ByteToUUID(byteform);
		
		assertThat(uuidFromBytes, is(uuid));
	}
}
