package com.github.seeker.configuration;

public interface ConsulConfiguration {
	/**
	 * IP address of a consul cluster member, used to join the cluster.
	 * 
	 * @return the IP of a consul cluster member
	 */
	String ip();

	/**
	 * Port used for the consul API.
	 * 
	 * @return port to connect to the cluster
	 */
	int port();
	
	/**
	 * The data center that this node is running in.
	 * 
	 * @return the name of the datacenter
	 */
	String datacenter();
}
