/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.app;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;

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
	private static final String THUMBNAIL_COMMAND = "thumb";
	private static final String DB_COMMAND = "db";

	public static void main(String[] args) {
		
		ArgumentParser parser  = ArgumentParsers.newFor("Si2").build();
		Subparsers subparsers = parser.addSubparsers().title("subcommands");
		
		Subparser loader = subparsers.addParser("loader").description("loads files for processing").setDefault(COMMAND_ATTRIBUTE, LOADER_COMMAND);
		loader.addArgument("--id").required(true).action(Arguments.store()).help("The id of this loader, used to get anchors from consul");
		
		subparsers.addParser("processor").description("Processes files from the queue").setDefault(COMMAND_ATTRIBUTE, PROCESSOR_COMMAND);
		Subparser thumb = subparsers.addParser("thumb").description("Stores and retrieves thumbnails").setDefault(COMMAND_ATTRIBUTE, THUMBNAIL_COMMAND);
		thumb.addArgument("--thumb-dir").setDefault("thumbs").required(false).action(Arguments.store()).help("The directory to store thumbnails, defaults to the relative directory 'thumbs'");
		subparsers.addParser("db").description("Stores metadata entries in the database").setDefault(COMMAND_ATTRIBUTE, DB_COMMAND);

		try {
			processArgs(parser.parseArgs(args));
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
	}
	
	private static void processArgs(Namespace namespace) {
		System.out.println(namespace);
		
		ConnectionProvider connectionProvider = new ConnectionProvider(new ConfigurationBuilder().getConsulConfiguration());
		
		if(LOADER_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new FileLoader(namespace.getString("id"), connectionProvider);
				System.exit(0);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(PROCESSOR_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new FileProcessor(connectionProvider);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(THUMBNAIL_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new ThumbnailNode(connectionProvider, namespace.getString("thumb_dir"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(DB_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new DBNode(connectionProvider);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
