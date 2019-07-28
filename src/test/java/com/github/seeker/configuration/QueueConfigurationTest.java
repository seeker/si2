package com.github.seeker.configuration;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.rabbitmq.client.Channel;

@RunWith(MockitoJUnitRunner.class)
public class QueueConfigurationTest {
	private static final String HASH_QUEUE_NAME = "foo";
	private static final String THUMBNAIL_QUEUE_NAME = "bar";
	private static final String FILE_QUEUE_NAME = "baz";
	
	@Mock
	private Channel channel;

	@Mock
	private ConsulClient consulClient;
	
	private QueueConfiguration cut;
	private QueueConfiguration cutIntegration;

	private String prefixWithIntegration(String queueName) {
		return "integration-" + queueName;
	}
	
	@Before
	public void setUp() throws Exception {
		when(consulClient.getKvAsString(eq("config/rabbitmq/queue/hash"))).thenReturn(HASH_QUEUE_NAME);
		when(consulClient.getKvAsString(eq("config/rabbitmq/queue/thumbnail"))).thenReturn(THUMBNAIL_QUEUE_NAME);
		when(consulClient.getKvAsString(eq("config/rabbitmq/queue/loader-file-feed"))).thenReturn(FILE_QUEUE_NAME);
		
		cut = new QueueConfiguration(channel, consulClient);
		cutIntegration = new QueueConfiguration(channel, consulClient, true);
	}

	@Test
	public void notInIntegrationModeByDefault() throws Exception {
		assertThat(cut.isIntegrationConfig(), is(false));
	}
	
	@Test
	public void integrationModeIsSet() throws Exception {
		assertThat(cutIntegration.isIntegrationConfig(), is(true));
	}
	
	@Test
	public void queueNameForHash() throws Exception {
		assertThat(cut.getQueueName(ConfiguredQueues.hashes), is(HASH_QUEUE_NAME));
	}
	
	@Test
	public void queueNameForThumbnail() throws Exception {
		assertThat(cut.getQueueName(ConfiguredQueues.thumbnails), is(THUMBNAIL_QUEUE_NAME));
	}
	
	@Test
	public void queueNameForFiles() throws Exception {
		assertThat(cut.getQueueName(ConfiguredQueues.files), is(FILE_QUEUE_NAME));
	}
	
	@Test
	public void queueNameForHashIntegration() throws Exception {
		assertThat(cutIntegration.getQueueName(ConfiguredQueues.hashes), is(prefixWithIntegration(HASH_QUEUE_NAME)));
	}
	
	@Test
	public void queueNameForThumbnailIntegration() throws Exception {
		assertThat(cutIntegration.getQueueName(ConfiguredQueues.thumbnails), is(prefixWithIntegration(THUMBNAIL_QUEUE_NAME)));
	}
	
	@Test
	public void queueNameForFilesIntegration() throws Exception {
		assertThat(cutIntegration.getQueueName(ConfiguredQueues.files), is(prefixWithIntegration(FILE_QUEUE_NAME)));
	}
	
	@Test(expected=IllegalStateException.class)
	public void noNameForQueue() throws Exception {
		cutIntegration.getQueueName(null);
	}
}
