/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.messaging;

import java.io.DataInput;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import com.github.seeker.persistence.document.Hash;
import com.google.common.io.ByteStreams;

/**
 * Packs and unpacks hash messages.
 */
public class HashMessageHelper {
	public Map<String, Hash> decodeHashMessage(Map<String, Object> headers, byte[] body) throws IOException, NoSuchAlgorithmException {
		String hashHeader = headers.get(MessageHeaderKeys.HASH_ALGORITHMS).toString();
		
		String[] hashesFromHeader = hashHeader.split(",");

		DataInput in  = ByteStreams.newDataInput(body);
		Map<String, Hash> hashes = new HashMap<String, Hash>();
		
		for(String hash : hashesFromHeader) {
			//TODO use length cache
			MessageDigest md = MessageDigest.getInstance(hash);
			byte[] digest = new byte[md.getDigestLength()];
			
			in.readFully(digest);
			hashes.put(hash, new Hash(hash, digest, "1"));
		}
		
		return hashes;
	}
	
	public String getAnchor(Map<String, Object> headers) {
		return headers.get(MessageHeaderKeys.ANCHOR).toString();
	}
	
	public String getRelativePath(Map<String, Object> headers) {
		return headers.get(MessageHeaderKeys.ANCHOR_RELATIVE_PATH).toString();
	}
}
