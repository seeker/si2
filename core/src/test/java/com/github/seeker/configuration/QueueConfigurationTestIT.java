/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.Objects;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
		ConsulConfiguration consulConfig = new ConfigurationBuilder().getConsulConfiguration();
		connectionProvider = new ConnectionProvider(consulConfig, new VaultIntegrationCredentials(Approle.integration),
				consulConfig.overrideVirtualBoxAddress());

		rabbitConn = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.integration).newConnection();
	}

	@AfterAll
	public static void tearDownAfterClass() throws Exception {
		if (Objects.nonNull(rabbitConn)) {
			rabbitConn.close();
		}
	}

	@BeforeEach
	public void setUp() throws Exception {
		cutChan = rabbitConn.createChannel();
		testChan = rabbitConn.createChannel();

		cut = new QueueConfiguration(cutChan, true);
	}

	@AfterEach
	public void tearDown() throws Exception {
		cut.deleteAllQueues();
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

	public void queueDeleted() throws Exception {
		try {
			testChan.queueDeclarePassive(cut.getQueueName(ConfiguredQueues.persistence));
		} catch (IOException e) {
			fail("Queue was not delcared");
		}

		cut.deleteQueue(ConfiguredQueues.persistence);
		assertThrows(IOException.class, () -> {
			testChan.queueDeclarePassive(cut.getQueueName(ConfiguredQueues.persistence));
		});
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
