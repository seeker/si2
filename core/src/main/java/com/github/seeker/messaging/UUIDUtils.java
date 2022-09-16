/* The MIT License (MIT)
 * Copyright (c) 2022 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.messaging;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Utility class to assist with transformation of {@link UUID}.
 */
public class UUIDUtils {
	/***
	 * Convert a {@link UUID} to byte array representation.
	 * 
	 * @param uuid to convert
	 * @return Array containing the UUID
	 */
	public static byte[] UUIDtoByte(UUID uuid) {
		ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
		
		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());
		
		return buffer.array();
	}
	
	/**
	 * Convert a byte array containing a {@link UUID} to an instance.
	 * @param uuidAsByte array containing the {@link UUID}
	 * @return the converted {@link UUID}
	 */
	public static UUID ByteToUUID(byte[] uuidAsByte) {
		ByteBuffer buffer = ByteBuffer.wrap(uuidAsByte);
		
		return new UUID(buffer.getLong(), buffer.getLong());
	}
}
