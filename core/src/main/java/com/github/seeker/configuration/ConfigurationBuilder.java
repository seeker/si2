/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import org.cfg4j.provider.ConfigurationProvider;
import org.cfg4j.provider.ConfigurationProviderBuilder;
import org.cfg4j.source.ConfigurationSource;
import org.cfg4j.source.classpath.ClasspathConfigurationSource;
import org.cfg4j.source.compose.FallbackConfigurationSource;
import org.cfg4j.source.context.filesprovider.ConfigFilesProvider;
import org.cfg4j.source.files.FilesConfigurationSource;
import org.cfg4j.source.inmemory.InMemoryConfigurationSource;

public class ConfigurationBuilder {
	private static final String CONFIG_FILE_NAME = "si2.yaml";
	
	private final ConfigurationProvider configProvider;
	
	public ConfigurationBuilder() {
		this.configProvider = provideConfigurationProvider();
	}

	/**
	 * Provide a configuration object for the application. Reads a configuration file and a in memory fallback.
	 */
	private ConfigurationProvider provideConfigurationProvider() {
		ConfigFilesProvider configFilesProvider = () -> Arrays.asList(Paths.get(CONFIG_FILE_NAME));
		ConfigurationSource fileConfig = new FilesConfigurationSource(configFilesProvider);
		ConfigurationSource classPathConfig = new ClasspathConfigurationSource(configFilesProvider);
		
		ConfigurationSource configWithFallback = new FallbackConfigurationSource(classPathConfig, fileConfig);

		return new ConfigurationProviderBuilder().withConfigurationSource(configWithFallback).build();
	}
	
	public ConsulConfiguration getConsulConfiguration() {
		return configProvider.bind("consul", ConsulConfiguration.class);
	}
}
