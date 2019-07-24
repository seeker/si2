/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.io;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.jimfs.Jimfs;

public class ImageFileFilterTest {
	private ImageFileFilter cut;
	private FileSystem fs;

	@Before
	public void setUp() throws Exception {
		fs = Jimfs.newFileSystem();
		cut = new ImageFileFilter();
	}

	@After
	public void tearDown() throws Exception {
		fs.close();
	}

	private Path createFile(String fileName) throws IOException {
		Path path = fs.getPath(fileName);
		Files.createFile(path);
		return path;
	}

	@Test
	public void testJpg() throws Exception {
		assertThat(cut.accept(createFile("foo.jpg")), is(true));
	}

	@Test
	public void testJpeg() throws Exception {
		assertThat(cut.accept(createFile("foo.jpeg")), is(true));
	}

	@Test
	public void testPng() throws Exception {
		assertThat(cut.accept(createFile("foo.png")), is(true));
	}

	@Test
	public void testGif() throws Exception {
		assertThat(cut.accept(createFile("foo.gif")), is(true));
	}

	@Test
	public void testNonLowerCase() throws Exception {
		assertThat(cut.accept(createFile("foo.Jpg")), is(true));
	}

	@Test
	public void testNonImageExtension() throws Exception {
		assertThat(cut.accept(createFile("foo.txt")), is(false));
	}
}
