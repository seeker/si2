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
	
	/**
	 * When using VirtualBox for development on Windows, the NATed address is localhost,
	 * but consul still reports 10.2.0.15.
	 * If this is set to true, all 10.2.0.15 IP addresses will be re-written to 127.0.0.1. 
	 * 
	 * This option should only be set to true for local development.
	 * 
	 * @return true if 10.2.0.15 should be rewritten to 127.0.0.1
	 */
	boolean overrideVirtualBoxAddress();
}
