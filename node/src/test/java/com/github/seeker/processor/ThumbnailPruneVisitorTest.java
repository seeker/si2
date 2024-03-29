/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.processor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.github.seeker.persistence.MongoDbMapper;
import com.google.common.jimfs.Jimfs;

@RunWith(MockitoJUnitRunner.class)
public class ThumbnailPruneVisitorTest {
	private static final String BASE_THUMB_PATH = "thumbs";

	private static final String IMAGE_ID_A = "95b9261c-58e3-4184-89a5-93603602b7ae";
	private static final String IMAGE_ID_B = "938e003d-ac55-426d-b177-6af12c66e40d";
	private static final String IMAGE_ID_C = "c685b6d5-8b0b-4447-a125-e775d78010fb";

	private static final String NOT_A_THUMBNAIL = "someotherfile.txt";
	
	private Path imagePathA;
	private Path imagePathB;
	private Path imagePathC;
	private Path notThumbnailA;
	private Path notThumbnailB;
	private Path notInDir;

	private FileSystem fs;
	private ThumbnailPruneVisitor cut;

	@Mock
	private MongoDbMapper mapper;

	@Before
	public void setUp() throws Exception {
		fs = Jimfs.newFileSystem();

		when(mapper.hasImageId(IMAGE_ID_A)).thenReturn(false);
		when(mapper.hasImageId(IMAGE_ID_B)).thenReturn(true);
		when(mapper.hasImageId(IMAGE_ID_C)).thenReturn(false);

		setUpTestFilesystem();

		cut = new ThumbnailPruneVisitor(mapper, fs);
	}

	private void setUpTestFilesystem() throws IOException {
		Path baseDir = fs.getPath(BASE_THUMB_PATH);

		imagePathA = baseDir.resolve("9").resolve("95").resolve(IMAGE_ID_A);
		imagePathB = baseDir.resolve("9").resolve("93").resolve(IMAGE_ID_B);
		imagePathC = baseDir.resolve("c").resolve("c6").resolve(IMAGE_ID_C);
		
		notThumbnailA = baseDir.resolve(NOT_A_THUMBNAIL);
		notThumbnailB = baseDir.resolve("9").resolve("95").resolve(NOT_A_THUMBNAIL);
		
		notInDir = baseDir.resolve("other").resolve(IMAGE_ID_A);

		Files.createDirectory(baseDir);
		Files.createDirectories(baseDir.resolve("other"));
		Files.createDirectories(baseDir.resolve("9").resolve("93"));
		Files.createDirectories(baseDir.resolve("9").resolve("95"));
		Files.createDirectories(baseDir.resolve("c").resolve("c6"));

		Files.createFile(imagePathA);
		Files.createFile(imagePathB);
		Files.createFile(imagePathC);

		Files.createFile(notThumbnailA);
		Files.createFile(notThumbnailB);
		Files.createFile(notInDir);

		assertThat(Files.exists(imagePathA), is(true));
		assertThat(Files.exists(imagePathB), is(true));
		assertThat(Files.exists(imagePathC), is(true));
		assertThat(Files.exists(notThumbnailA), is(true));
		assertThat(Files.exists(notThumbnailB), is(true));
		assertThat(Files.exists(notInDir), is(true));
	}

	@Test
	public void pruneThumbnails() throws Exception {
		Files.walkFileTree(fs.getPath(BASE_THUMB_PATH), cut);

		assertThat(Files.exists(imagePathA), is(false));
		assertThat(Files.exists(imagePathB), is(true));
		assertThat(Files.exists(imagePathC), is(false));
	}

	@Test
	public void getPrunedThumbnailCount() throws Exception {
		Files.walkFileTree(fs.getPath(BASE_THUMB_PATH), cut);
		
		assertThat(cut.getPrunedThumbnailCount(), is(2L));
	}

	@Test
	public void nonThumbnailFileInBaseDirIsNotDeleted() throws Exception {
		Files.walkFileTree(fs.getPath(BASE_THUMB_PATH), cut);

		assertThat(Files.exists(notThumbnailA), is(true));
	}

	@Test
	public void nonThumbnailFileInSubDirIsNotDeleted() throws Exception {
		Files.walkFileTree(fs.getPath(BASE_THUMB_PATH), cut);

		assertThat(Files.exists(notThumbnailB), is(true));
	}

	@Test
	public void thumbnailFileOutSideSubDirIsNotDeleted() throws Exception {
		Files.walkFileTree(fs.getPath(BASE_THUMB_PATH), cut);

		assertThat(Files.exists(notInDir), is(true));
	}
}
