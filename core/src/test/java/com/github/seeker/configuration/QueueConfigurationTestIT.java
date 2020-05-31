/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Objects;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;
import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

public class QueueConfigurationTestIT {
	private static final Logger LOGGER = LoggerFactory.getLogger(QueueConfigurationTestIT.class);

	private static ConnectionProvider connectionProvider;
	private static Connection rabbitConn;

	private QueueConfiguration cut;
	private Channel cutChan;
	private Channel testChan;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ConsulConfiguration consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		connectionProvider = new ConnectionProvider(consulConfig, new VaultIntegrationCredentials(Approle.integration),
				consulConfig.overrideVirtualBoxAddress());

		rabbitConn = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.integration).newConnection();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (Objects.nonNull(rabbitConn)) {
			rabbitConn.close();
		}
	}

	@Before
	public void setUp() throws Exception {
		cutChan = rabbitConn.createChannel();
		testChan = rabbitConn.createChannel();

		cut = new QueueConfiguration(cutChan, true);
	}

	@After
	public void tearDown() throws Exception {
		for (ConfiguredQueues queue : ConfiguredQueues.values()) {
			String queueName = cut.getQueueName(queue);

			try {
				testChan.queueDelete(queueName);
				testChan.close();
			} catch (AlreadyClosedException e) {
				LOGGER.debug("Queue {} already closed", queueName);
			}
		}

		cutChan.close();
	}

	@Test
	public void allQueuesCreated() throws Exception {
		for (ConfiguredQueues queue : ConfiguredQueues.values()) {
			String queueName = cut.getQueueName(queue);
			DeclareOk ok = testChan.queueDeclarePassive(queueName);

			assertThat(ok.getQueue(), is(queueName));
		}
	}

	@Test(expected = IOException.class)
	public void queueDeleted() throws Exception {
		try {
			testChan.queueDeclarePassive(cut.getQueueName(ConfiguredQueues.hashes));
		} catch (IOException e) {
			fail("Queue was not delcared");
		}

		cut.deleteQueue(ConfiguredQueues.hashes);

		testChan.queueDeclarePassive(cut.getQueueName(ConfiguredQueues.hashes));
	}

	@Test
	public void allQueuesDeleted() throws Exception {
		cut.deleteAllQueues();

		for (ConfiguredQueues queue : ConfiguredQueues.values()) {
			try {
				testChan.queueDeclarePassive(cut.getQueueName(queue));
				fail("Queue " + queue.toString() + " was not deleted");
			} catch (IOException e) {
				LOGGER.debug("Queue {} deleted (IOEx)", queue);
			} catch (AlreadyClosedException e) {
				LOGGER.debug("Queue {} deleted (AlreadyClosedEx)", queue);
			}
		}
	}
}
