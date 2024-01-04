/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence.document;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FileLoaderJobTest {
	private FileLoaderJob cut;

	private static final String ANCHOR = "foo";
	private static final String PATH = "bar";

	@BeforeEach
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

	public void jobidCannotBeNull() throws Exception {
		assertThrows(NullPointerException.class, () -> {
			new FileLoaderJob(null, ANCHOR, PATH);
		});
	}

	public void anchorCannotBeNull() throws Exception {
		assertThrows(NullPointerException.class, () -> {
			new FileLoaderJob(null, PATH);
		});
	}

	public void pathCannotBeNull() throws Exception {
		assertThrows(NullPointerException.class, () -> {
			new FileLoaderJob(ANCHOR, null);
		});
	}
	
	@Test
	public void generateThumbnailsSetViaConstructor() throws Exception {
		assertThat(new FileLoaderJob(ANCHOR, PATH, true).isGenerateThumbnail(), is(true));
	}
}
