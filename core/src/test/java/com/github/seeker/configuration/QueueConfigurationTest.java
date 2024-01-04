package com.github.seeker.configuration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.seeker.configuration.QueueConfiguration.ConfiguredExchanges;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
public class QueueConfigurationTest {
	private static final String PERSISTENCE_QUEUE_NAME = "persistence";
	private static final String FILE_QUEUE_NAME = "fileDigest";
	private static final String FILE_RESIZE_NAME = "fileResize";
	
	@Mock
	private Channel channel;

	private QueueConfiguration cut;
	private QueueConfiguration cutIntegration;

	private String prefixWithIntegration(String queueName) {
		return "integration-" + queueName;
	}
	
	@BeforeEach
	public void setUp() throws Exception {
		cut = new QueueConfiguration(channel);
		cutIntegration = new QueueConfiguration(channel, true);
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
	public void queueNameForPersistence() throws Exception {
		assertThat(cut.getQueueName(ConfiguredQueues.persistence), is(PERSISTENCE_QUEUE_NAME));
	}
	
	@Test
	public void queueNameForFiles() throws Exception {
		assertThat(cut.getQueueName(ConfiguredQueues.fileDigest), is(FILE_QUEUE_NAME));
	}

	@Test
	public void queueNameForResize() throws Exception {
		assertThat(cut.getQueueName(ConfiguredQueues.fileResize), is(FILE_RESIZE_NAME));
	}
	
	@Test
	public void queueNameForPersistenceIntegration() throws Exception {
		assertThat(cutIntegration.getQueueName(ConfiguredQueues.persistence), is(startsWith(prefixWithIntegration(PERSISTENCE_QUEUE_NAME))));
	}
	
	@Test
	public void queueNameForFilesIntegration() throws Exception {
		assertThat(cutIntegration.getQueueName(ConfiguredQueues.fileDigest), is(startsWith(prefixWithIntegration(FILE_QUEUE_NAME))));
	}
	
	public void noNameForQueue() throws Exception {
		assertThrows(IllegalStateException.class, () -> {
			cutIntegration.getQueueName(null);
		});
	}
	
	@Test
	public void getLoaderCommandExchangeName() throws Exception {
		assertThat(cut.getExchangeName(ConfiguredExchanges.loaderCommand), is("loaderCommand"));
	}
	
	@Test
	public void getLoaderCommandIntegrationExchangeName() throws Exception {
		assertThat(cutIntegration.getExchangeName(ConfiguredExchanges.loaderCommand), is("integration-loaderCommand"));
	}
	
	@Test
	public void normalQueuesMustReturnSameName() throws Exception {
		String firstCall = cut.getQueueName(ConfiguredQueues.fileDigest);
		String secondCall = cut.getQueueName(ConfiguredQueues.fileDigest);
		
		assertThat(firstCall, is(secondCall));
	}
	
	@Test
	public void integrationQueuesMustReturnSameName() throws Exception {
		String firstCall = cutIntegration.getQueueName(ConfiguredQueues.fileDigest);
		String secondCall = cutIntegration.getQueueName(ConfiguredQueues.fileDigest);
		
		assertThat(firstCall, is(secondCall));
	}
}
