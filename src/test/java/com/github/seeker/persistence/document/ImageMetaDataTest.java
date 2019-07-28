package com.github.seeker.persistence.document;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static org.hamcrest.text.IsEmptyString.emptyString;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

public class ImageMetaDataTest {
	private ImageMetaData cut;
	
	@Before
	public void setUp() throws Exception {
		cut = new ImageMetaData();
	}

	@Test
	public void hashMapOnNewInstanceIsNotNull() throws Exception {
		assertThat(cut.getHashes(), is(anEmptyMap()));
	}

	@Test
	public void anchorOnNewInstanceIsEmpty() throws Exception {
		assertThat(cut.getAnchor(), is(emptyString()));
	}

	@Test
	public void pathOnNewInstanceIsEmpty() throws Exception {
		assertThat(cut.getPath(), is(emptyString()));
	}
}
