package com.github.seeker.persistence.document;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static org.hamcrest.text.IsEmptyString.emptyString;
import static org.junit.Assert.*;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

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

	@Test
	public void doesNotHaveThumbnail() throws Exception {
		assertThat(cut.hasThumbnail(), is(false));
	}
	
	@Test
	public void hasThumbnail() throws Exception {
		cut.setThumbnailId(UUID.randomUUID());
		
		assertThat(cut.hasThumbnail(), is(true));
	}
	
	@Test
	public void verifyEqualsAndHash() throws Exception {
		EqualsVerifier.forClass(ImageMetaData.class).allFieldsShouldBeUsedExcept("id", "creationTime", "fileSize", "hashes", "thumbnailId", "tags").suppress(Warning.NONFINAL_FIELDS).verify();
	}
}
