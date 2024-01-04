package com.github.seeker.persistence.document;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static org.hamcrest.text.IsEmptyString.emptyString;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class ImageMetaDataTest {
	private ImageMetaData cut;
	
	@BeforeEach
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
	public void verifyEqualsAndHash() throws Exception {
		EqualsVerifier.forClass(ImageMetaData.class).withIgnoredFields("id", "creationTime", "fileSize", "hashes", "thumbnail", "tags", "imageId")
				.suppress(Warning.NONFINAL_FIELDS).verify();
	}
}
