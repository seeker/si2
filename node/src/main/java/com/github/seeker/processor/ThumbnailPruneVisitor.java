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
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.github.seeker.persistence.MongoDbMapper;

public class ThumbnailPruneVisitor extends SimpleFileVisitor<Path> {
	private static final Pattern uuid = Pattern.compile("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$");
	private static final Pattern oneDigitHex = Pattern.compile("^[a-f0-9]{1}$");
	private static final Pattern twoDigitHex = Pattern.compile("^[a-f0-9]{2}$");
	private Predicate<String> validThumbnailName;
	private Predicate<String> validOneDigitHex;
	private Predicate<String> validTwoDigitHex;
	private final MongoDbMapper mapper;
	private final FileSystemProvider fsProvider;

	private long prunedThumbnailCount;

	public ThumbnailPruneVisitor(MongoDbMapper mapper, FileSystem fs) {
		this.mapper = mapper;
		this.fsProvider = fs.provider();

		this.validThumbnailName = uuid.asPredicate();
		this.validOneDigitHex = oneDigitHex.asPredicate();
		this.validTwoDigitHex = twoDigitHex.asPredicate();

	}

	public ThumbnailPruneVisitor(MongoDbMapper mapper) {
		this(mapper, FileSystems.getDefault());
	}
	
	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		String filename = file.getFileName().toString();

		if (!isValidThumbnail(filename, file)) {
			return FileVisitResult.CONTINUE;
		}

		if(!mapper.hasImageId(filename)) {
			fsProvider.delete(file);
			prunedThumbnailCount++;
		}
		
		return FileVisitResult.CONTINUE;
	}

	private boolean isValidThumbnail(String filename, Path fullpath) {
		return validThumbnailName.test(filename) && isValidDirectory(fullpath);
	}

	/**
	 * Check if the directory matches the pattern /{one hex digit}/{two hex
	 * digits}/thumbnail
	 * 
	 * The variables use are as follows: /firstDirectory/secondDirectory/thumbnail
	 * 
	 * @param fullpath the full path including the filename of the thumbnail
	 * @return true if the underlying directories are have valid names
	 */
	private boolean isValidDirectory(Path fullpath) {
		Path secondDirectory = fullpath.getParent();

		if (Objects.isNull(secondDirectory)) {
			return false;
		}

		Path firstDirectory = secondDirectory.getParent();

		if (Objects.isNull(firstDirectory)) {
			return false;
		}

		String secondHex = secondDirectory.getFileName().toString();
		String firstHex = firstDirectory.getFileName().toString();

		if (!validTwoDigitHex.test(secondHex)) {
			return false;
		}

		return validOneDigitHex.test(firstHex);
	}

	/**
	 * Get the number of thumbnail files that this visitor has pruned. The counter
	 * is not synchronized and the value will only be reliable once the file walk
	 * has finished.
	 * 
	 * @return the number of thumbnail files pruned
	 */
	public long getPrunedThumbnailCount() {
		return prunedThumbnailCount;
	}
}
