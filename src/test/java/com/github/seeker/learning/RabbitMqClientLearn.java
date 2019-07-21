package com.github.seeker.learning;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConsulConfiguration;
import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.option.QueryOptions;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

public class RabbitMqClientLearn {
	private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqClientLearn.class);
	
	private static final String QUEUE_NAME = "learning-queue";
	private static final String TEST_MESSAGE = "foo bar";
	
	private static ConnectionFactory connFactory;
	private static Connection conn;
	private static Channel channel;
	
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		ConsulConfiguration consulConfiguration = new ConfigurationBuilder().getConsulConfiguration();

		Consul client = Consul.builder().withHostAndPort(HostAndPort.fromParts(consulConfiguration.ip(), consulConfiguration.port())).build();
		
		HealthClient healthClient = client.healthClient();
		List<ServiceHealth> healtyMongoDbServers = healthClient.getHealthyServiceInstances("rabbitmq",QueryOptions.blockSeconds(5, "bar").datacenter("vagrant").build()).getResponse();

		String serverAddress = healtyMongoDbServers.get(0).getNode().getAddress();
		int serverPort = healtyMongoDbServers.get(0).getService().getPort();
		
		connFactory = new ConnectionFactory();
		connFactory.setUsername("integration");
		connFactory.setPassword(client.keyValueClient().getValue("config/rabbitmq/users/integration").get().getValueAsString().get());
		connFactory.setHost(serverAddress);
		connFactory.setPort(serverPort);
		
		LOGGER.info("Connecting to Rabbitmq server {}:{}", serverAddress, serverPort);
		
		conn = connFactory.newConnection();
		channel = conn.createChannel();
		
		channel.queueDeclare(QUEUE_NAME, false, false, false, null);
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		conn.close();
	}
	
	@Test
	public void sendAndReceiveMessage() throws Exception {
		channel.basicPublish("", QUEUE_NAME, null, TEST_MESSAGE.getBytes());
		
		GetResponse response = channel.basicGet(QUEUE_NAME, true);
		
		String message = new String(response.getBody(), "UTF-8");
		assertThat(message, is(TEST_MESSAGE));
	}
}
