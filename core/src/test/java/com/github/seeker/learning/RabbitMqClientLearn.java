package com.github.seeker.learning;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulClient.ConfiguredService;
import com.github.seeker.configuration.ConsulConfiguration;
import com.orbitz.consul.model.health.ServiceHealth;
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
	
	
	@BeforeAll
	public static void setUpClass() throws Exception {
		ConsulConfiguration consulConfiguration = new ConfigurationBuilder().getConsulConfiguration();

		ConsulClient consulClient = new ConsulClient(consulConfiguration);
		ServiceHealth rabbitMqService = consulClient.getFirstHealtyInstance(ConfiguredService.rabbitmq);
		
		String serverAddress = rabbitMqService.getNode().getAddress();
		int serverPort = rabbitMqService.getService().getPort();
		
		connFactory = new ConnectionFactory();
		connFactory.setUsername("integration");
		connFactory.setPassword(consulClient.getKvAsString("config/rabbitmq/users/integration"));
		connFactory.setHost(serverAddress);
		connFactory.setPort(serverPort);
		
		LOGGER.info("Connecting to Rabbitmq server {}:{}", serverAddress, serverPort);
		
		conn = connFactory.newConnection();
		channel = conn.createChannel();
		
		channel.queueDeclare(QUEUE_NAME, false, false, false, null);
	}
	
	@AfterAll
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
