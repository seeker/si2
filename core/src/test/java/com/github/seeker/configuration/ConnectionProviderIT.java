/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.seeker.configuration.VaultIntegrationCredentials.Approle;
import com.github.seeker.persistence.MongoDbMapper;
import com.rabbitmq.client.Connection;

public class ConnectionProviderIT {
	private static ConsulConfiguration consulConfig;
	private ConnectionProvider cut;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		consulConfig =  new ConfigurationBuilder().getConsulConfiguration();
	}

	@Before
	public void setUp() throws Exception {
		cut = new ConnectionProvider(consulConfig, new VaultIntegrationCredentials(Approle.integration));
	}

	@Test
	public void mongodbIntegrationMapperIsValid() throws Exception {
		MongoDbMapper mapper = cut.getIntegrationMongoDbMapper();
		
		assertThat(mapper, is(notNullValue()));
	}

	@Test
	public void mongodbMapperIsValid() throws Exception {
		MongoDbMapper mapper = cut.getMongoDbMapper();
		
		assertThat(mapper, is(notNullValue()));
	}
	
	@Test
	public void consulClientValid() throws Exception {
		ConsulClient consul = cut.getConsulClient();
		
		assertThat(consul, is(notNullValue()));
	}
	
	@Test
	public void rabbitMQClientValid() throws Exception {
		Connection rabbitConn = cut.getRabbitMQConnectionFactory(RabbitMqRole.integration).newConnection();
		
		assertThat(rabbitConn.isOpen(), is(true));
	}
}
