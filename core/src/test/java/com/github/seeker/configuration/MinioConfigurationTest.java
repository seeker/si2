package com.github.seeker.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.seeker.configuration.MinioConfiguration.BucketKey;

public class MinioConfigurationTest {
	private Map<BucketKey, String> productionBuckets;
	private Map<BucketKey, String> integrationBuckets;

	@BeforeEach
	public void setUp() throws Exception {
		productionBuckets = MinioConfiguration.productionBuckets();
		integrationBuckets = MinioConfiguration.integrationTestBuckets();
	}

	@Test
	public void productionImageBucketName() throws Exception {
		assertThat(productionBuckets.get(MinioConfiguration.BucketKey.Si2), is("si2"));
	}

	@Test
	public void allProductionBucketsHaveName() throws Exception {
		for (BucketKey key : BucketKey.values()) {
			assertNotNull("No production bucket for key " + key.toString() + " defined", productionBuckets.get(key));
		}
	}

	@Test
	public void allIntegrationBucketsHaveName() throws Exception {
		for (BucketKey key : BucketKey.values()) {
			assertNotNull("No integration bucket for key " + key.toString() + " defined", integrationBuckets.get(key));
		}
	}

	@Test
	public void integrationTestImageBucketName() throws Exception {
		assertThat(integrationBuckets.get(MinioConfiguration.BucketKey.Si2), is("integration-si2"));
	}

}
