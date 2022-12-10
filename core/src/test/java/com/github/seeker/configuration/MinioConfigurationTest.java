package com.github.seeker.configuration;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.github.seeker.configuration.MinioConfiguration.BucketKey;

public class MinioConfigurationTest {
	private Map<BucketKey, String> productionBuckets;
	private Map<BucketKey, String> integrationBuckets;

	@Before
	public void setUp() throws Exception {
		productionBuckets = MinioConfiguration.productionBuckets();
		integrationBuckets = MinioConfiguration.integrationTestBuckets();
	}

	@Test
	public void productionImageBucketName() throws Exception {
		assertThat(productionBuckets.get(MinioConfiguration.BucketKey.Image), is("si2-images"));
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
		assertThat(integrationBuckets.get(MinioConfiguration.BucketKey.Image), is("integration-si2-images"));
	}

}
