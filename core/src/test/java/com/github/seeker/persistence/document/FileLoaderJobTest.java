/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence.document;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class FileLoaderJobTest {
	private FileLoaderJob cut;

	private static final String ANCHOR = "foo";
	private static final String PATH = "bar";

	@Before
	public void setUp() throws Exception {
		cut = new FileLoaderJob(ANCHOR, PATH);
	}

	@Test
	public void notCompleteByDefault() throws Exception {
		assertThat(cut.isCompleted(), is(false));
	}

	@Test
	public void markingJobAsCompleteSetsState() throws Exception {
		cut.markCompleted();

		assertThat(cut.isCompleted(), is(true));
	}

	@Test(expected = NullPointerException.class)
	public void jobidCannotBeNull() throws Exception {
		new FileLoaderJob(null, ANCHOR, PATH);
	}

	@Test(expected = NullPointerException.class)
	public void anchorCannotBeNull() throws Exception {
		new FileLoaderJob(null, PATH);
	}

	@Test(expected = NullPointerException.class)
	public void pathCannotBeNull() throws Exception {
		new FileLoaderJob(ANCHOR, null);
	}
}
