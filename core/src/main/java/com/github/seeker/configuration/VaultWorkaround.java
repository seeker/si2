/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;

/**
 * This class contains workarounds for issues with com.bettercloud.vault.Vault
 */
public class VaultWorkaround {
	private static final Logger LOGGER = LoggerFactory.getLogger(VaultWorkaround.class);

	private static final String DEFAULT_RABBITMQ_MOUNT = "rabbitmq";

	private final Vault vault;

	public VaultWorkaround(Vault vault) {
		this.vault = vault;
	}

	public LogicalResponse readRabbitMqCredentials(String role) throws VaultException {
		return readRabbitMqCredentials(DEFAULT_RABBITMQ_MOUNT, role);
	}

	public LogicalResponse readRabbitMqCredentials(String mount, String role) throws VaultException {
		String rabbitMqCredsPath = "/" + mount + "/creds/" + role.toString();

		LOGGER.debug("Requesting RabbitMQ credentials from {}", rabbitMqCredsPath);
		LogicalResponse rabbitCreds = vault.logical().read(rabbitMqCredsPath);
		int status = rabbitCreds.getRestResponse().getStatus();

		if (status == 400) {
			throw new VaultException("Response returned '400 Bad Request'", 400);
		} else if (rabbitCreds.getRestResponse().getStatus() != 200) {
			LOGGER.error("Failed to read credentails from Vault with response code {}", status);
			throw new VaultException("Failed with response " + Integer.toString(status), status);
		}

		return rabbitCreds;
	}

	/**
	 * Renew the lease with the given id for the specified increment.
	 * 
	 * @param leaseId   for the lease to renew
	 * @param increment the new TTL value for the lease
	 * @return the renew response
	 * @throws VaultException if there is an issue renewing the lease
	 */
	public LogicalResponse renewLease(String leaseId, long increment) throws VaultException {
		Map<String, Object> renewParams = new HashMap<>();
		renewParams.put("lease_id", leaseId);
		renewParams.put("increment", increment);

		LogicalResponse renewResponse = vault.logical().write("sys/leases/renew", renewParams);
		int statusCode = renewResponse.getRestResponse().getStatus();

		if (statusCode == 200) {
			LOGGER.debug("Successfully renewed  lease {}", leaseId);
		} else {
			LOGGER.error("Failed to renew lease {} with status code {}", leaseId, statusCode);
		}

		return renewResponse;
	}
}
