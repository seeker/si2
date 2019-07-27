/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.app;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

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

	public static void main(String[] args) {
		
		ArgumentParser parser  = ArgumentParsers.newFor("Si2").build();
		Subparsers subparsers = parser.addSubparsers().title("subcommands");
		
		Subparser loader = subparsers.addParser("loader").description("loads files for processing").setDefault(COMMAND_ATTRIBUTE, LOADER_COMMAND);
		loader.addArgument("--id").required(true).action(Arguments.store()).help("The id of this loader, used to get anchors from consul");
		
		subparsers.addParser("processor").description("Processes files from the queue").setDefault(COMMAND_ATTRIBUTE, PROCESSOR_COMMAND);
		subparsers.addParser("thumb").description("Stores and retrieves thumbnails").setDefault(COMMAND_ATTRIBUTE, THUMBNAIL_COMMAND);

		try {
			processArgs(parser.parseArgs(args));
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
	}
	
	private static void processArgs(Namespace namespace) {
		System.out.println(namespace);
		
		if(LOADER_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new FileLoader(namespace.getString("id"));
				System.exit(0);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(PROCESSOR_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new FileProcessor();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(THUMBNAIL_COMMAND.equals(namespace.getString(COMMAND_ATTRIBUTE))) {
			try {
				new ThumbnailNode();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
