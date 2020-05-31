package com.github.seeker.messaging;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.configuration.VaultIntegrationCredentials;
import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

//TODO importing test code into other projects is tricky, separate project may work - but that may cause cyclic dependencies

/**
 * Shared code for messaging  integration tests. 
 */
public class MessageITBase {
	protected static ConnectionProvider connectionProvider;
	protected static Connection rabbitConn;
	
	protected QueueConfiguration queueConfig;
	
	protected ConsulClient consul;
	protected Channel channel;
	
	@BeforeClass
	public static void baseSetUpBeforeClass() throws Exception {
		ConsulConfiguration consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		
		connectionProvider = new ConnectionProvider(consulConfig, new VaultIntegrationCredentials(Approle.integration), consulConfig.overrideVirtualBoxAddress());
		rabbitConn = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.integration).newConnection();
	}

	@AfterClass
	public static void baseTearDownAfterClass() throws Exception {
		rabbitConn.close();
	}
	
	@Before
	public void baseSetUp() throws Exception {
		consul = connectionProvider.getConsulClient();
		channel = rabbitConn.createChannel();
		
		queueConfig = new QueueConfiguration(channel, true);
		assertThat(queueConfig.isIntegrationConfig(), is(true));
	}
}
