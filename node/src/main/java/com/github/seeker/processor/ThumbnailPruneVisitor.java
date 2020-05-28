/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.processor;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;

import com.github.seeker.persistence.MongoDbMapper;

public class ThumbnailPruneVisitor extends SimpleFileVisitor<Path> {
	private final MongoDbMapper mapper;
	private final FileSystemProvider fsProvider;

	public ThumbnailPruneVisitor(MongoDbMapper mapper, FileSystem fs) {
		this.mapper = mapper;
		this.fsProvider = fs.provider();
	}

	public ThumbnailPruneVisitor(MongoDbMapper mapper) {
		this(mapper, FileSystems.getDefault());
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		String filename = file.getFileName().toString();
		
		if(!mapper.hasImageId(filename)) {
			fsProvider.delete(file);
		}
		
		return FileVisitResult.CONTINUE;
	}
}
