/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.cfg4j.provider.ConfigurationProvider;
import org.cfg4j.provider.ConfigurationProviderBuilder;
import org.cfg4j.source.ConfigurationSource;
import org.cfg4j.source.classpath.ClasspathConfigurationSource;
import org.cfg4j.source.compose.FallbackConfigurationSource;
import org.cfg4j.source.context.filesprovider.ConfigFilesProvider;
import org.cfg4j.source.files.FilesConfigurationSource;

public class ConfigurationBuilder {
	private static final String CONFIG_FILE_NAME = "si2.yaml";
	
	private final ConfigurationProvider configProvider;
	
	/**
	 * Create a {@link ConfigurationBuilder}. Reads a configuration file from the classpath
	 * and falls back on the file system if not found.
	 * Uses the default file name si2.yaml
	 */
	public ConfigurationBuilder() {
		this(Paths.get(CONFIG_FILE_NAME));
	}
	
	/**
	 * Create a {@link ConfigurationBuilder}. Reads a configuration file from the classpath
	 * and falls back on the file system if not found.
	 * 
	 * Checks the classpath first with the path as a relative path, then the file system.
	 * 
	 * @param configurationFile The path to the configuration file
	 */
	public ConfigurationBuilder(Path configurationFile) {
		this.configProvider = provideConfigurationProvider(configurationFile);
	}

	/**
	 * Provide a configuration object for the application. Reads a configuration file from the classpath
	 * and falls back on the file system if not found.
	 * 
	 * Checks the classpath first with the path as a relative path, then the file system.
	 * 
	 * @param configurationFile The path to the configuration file
	 */
	private ConfigurationProvider provideConfigurationProvider(Path configurationFile) {
		ConfigFilesProvider configFilesProvider = () -> Arrays.asList(configurationFile);
		ConfigurationSource fileConfig = new FilesConfigurationSource(configFilesProvider);
		ConfigurationSource classPathConfig = new ClasspathConfigurationSource(configFilesProvider);
		
		ConfigurationSource configWithFallback = new FallbackConfigurationSource(classPathConfig, fileConfig);

		return new ConfigurationProviderBuilder().withConfigurationSource(configWithFallback).build();
	}
	
	public ConsulConfiguration getConsulConfiguration() {
		return configProvider.bind("consul", ConsulConfiguration.class);
	}
	
	public VaultCredentials getVaultCredentials() {
		return configProvider.bind("vault", VaultCredentials.class);
	}
}
