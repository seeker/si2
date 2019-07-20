package com.github.seeker.learning;

import static org.junit.Assert.assertThat;

import java.util.List;

import static org.hamcrest.Matchers.*;



import org.junit.BeforeClass;
import org.junit.Test;


import com.google.common.net.HostAndPort;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.health.ServiceHealth;

public class ConsulClientLearn {
	private static Consul client;
	private static AgentClient serviceAgent;
	
	private static final String TEST_SERVICE = "training-wheels";
	private static final String NODE_IP = "192.168.42.10";
	
	@BeforeClass
	public static void beforeClass() {
		client = Consul.builder().withHostAndPort(HostAndPort.fromParts(NODE_IP, 8500)).build();
		serviceAgent = client.agentClient();
		
		Registration service = ImmutableRegistration.builder().id(TEST_SERVICE).name(TEST_SERVICE).port(27017).build();
		
		serviceAgent.register(service);
	}

	@Test
	public void getHealthyService() {
		HealthClient healthClient = client.healthClient();
		assertThat(healthClient.getHealthyServiceInstances(TEST_SERVICE).getResponse(),hasSize(1));
	}
	
	@Test
	public void getIpOfDatabase() {
		HealthClient healthClient = client.healthClient();
		
		List<ServiceHealth> services  = healthClient.getHealthyServiceInstances(TEST_SERVICE).getResponse();
		
		assertThat(services.get(0).getNode().getAddress(), is(NODE_IP));
	}
}
