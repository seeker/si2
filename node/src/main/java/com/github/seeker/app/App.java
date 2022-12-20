/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.app;

import com.bettercloud.vault.VaultException;
import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.MinioConfiguration;
import com.github.seeker.persistence.MinioStore;

import io.minio.MinioClient;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class App {
	private static final String COMMAND_ATTRIBUTE = "command";
	private static final String LOADER_COMMAND = "loader";
	private static final String PROCESSOR_COMMAND = "processor";
	private static final String CUSTOM_HASH_COMMAND = "custom-hash";
	private static final String RESIZER_COMMAND = "resizer";
	private static final String DB_COMMAND = "db";

	public static void main(String[] args) {
		
		ArgumentParser parser  = ArgumentParsers.newFor("Si2").build();
		Subparsers subparsers = parser.addSubparsers().title("subcommands");
		
		Subparser loader = subparsers.addParser("loader").description("loads files for processing").setDefault(COMMAND_ATTRIBUTE, LOADER_COMMAND);
		loader.addArgument("--id").required(true).action(Arguments.store()).help("The id of this loader, used to get anchors from consul");
		
		subparsers.addParser("processor").description("Processes files from the queue").setDefault(COMMAND_ATTRIBUTE, PROCESSOR_COMMAND);
		subparsers.addParser("custom-hash").description("Processes pre-proceesed files from the queue").setDefault(COMMAND_ATTRIBUTE, CUSTOM_HASH_COMMAND);
		subparsers.addParser("db").description("Stores metadata entries in the database").setDefault(COMMAND_ATTRIBUTE, DB_COMMAND);
		subparsers.addParser("resizer").description("Resizes images for thumbnails and further processing").setDefault(COMMAND_ATTRIBUTE, RESIZER_COMMAND);

		try {
			processArgs(parser.parseArgs(args));
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		} catch (VaultException e) {
			e.printStackTrace();
		}
	}
	
	private static void processArgs(Namespace namespace) throws VaultException {
		System.out.println(namespace);
		
		ConfigurationBuilder configBuilder = new ConfigurationBuilder();
		ConsulConfiguration consulConfig = configBuilder.getConsulConfiguration();
		
		ConnectionProvider connectionProvider = new ConnectionProvider(consulConfig, configBuilder.getVaultCredentials(), consulConfig.overrideVirtualBoxAddress());
		MinioClient minioClient = connectionProvider.getMinioClient();
		MinioStore minio = new MinioStore(minioClient, MinioConfiguration.productionBuckets());

		if(LOADER_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new FileLoader(namespace.getString("id"), connectionProvider,
						configBuilder.getFileLoaderConfiguration(), minio);
				System.exit(0);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(PROCESSOR_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new MessageDigestHasher(connectionProvider, minio);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(CUSTOM_HASH_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new CustomHashProcessor(connectionProvider, minio);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (DB_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new DBNode(connectionProvider);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(RESIZER_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new ImageResizer(connectionProvider, minio);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
