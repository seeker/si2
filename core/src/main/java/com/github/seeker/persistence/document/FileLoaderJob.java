/* The MIT License (MIT)
 * Copyright (c) 2020 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.persistence.document;

import java.util.Objects;
import java.util.UUID;

import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.driver.MorphiumId;

/**
 * Stores jobs for the file loader class.
 */
@Entity
public class FileLoaderJob {
	@Id
	private MorphiumId id;

	@Index
	private UUID jobId;

	@Index
	private String anchor;

	@Index
	private String relativePath;

	@Index
	private boolean completed;

	@Index
	private boolean generateThumbnail;

	/**
	 * Generate a new {@link FileLoaderJob} with a random job id. The job will be
	 * created as not completed with no thumbnails being generated.
	 * 
	 * @param anchor       to load from
	 * @param relativePath the relative path in the anchor
	 */
	public FileLoaderJob(String anchor, String relativePath) {
		this(UUID.randomUUID(), anchor, relativePath);
	}

	/**
	 * Generate a new {@link FileLoaderJob} with a random job id. The job will be
	 * created as not completed.
	 * 
	 * @param anchor            to load from
	 * @param relativePath      the relative path in the anchor
	 * @param generateThumbnail if thumbnails should be generated with this job
	 */
	public FileLoaderJob(String anchor, String relativePath, boolean generateThumbnail) {
		this(UUID.randomUUID(), anchor, relativePath, generateThumbnail);
	}

	/**
	 * Generate a new {@link FileLoaderJob}. The job will be created as not
	 * completed with no thumbnails being generated.
	 * 
	 * @param anchor       to load from
	 * @param relativePath the relative path in the anchor
	 */
	public FileLoaderJob(UUID jobId, String anchor, String relativePath) {
		this(jobId, anchor, relativePath, false);
	}

	/**
	 * Generate a {@link FileLoaderJob} with the given job id. The job will be
	 * created as not completed.
	 * 
	 * @param jobId             to use for this job
	 * @param anchor            to load from
	 * @param relativePath      the relative path in the anchor
	 * @param generateThumbnail if thumbnails should be generated with this job
	 */
	public FileLoaderJob(UUID jobId, String anchor, String relativePath, boolean generateThumbnail) {
		Objects.requireNonNull(jobId, "Job id cannot be null!");
		Objects.requireNonNull(anchor, "Anchor cannot be null!");
		Objects.requireNonNull(relativePath, "Relative path cannot be null!");

		this.jobId = jobId;
		this.anchor = anchor;
		this.relativePath = relativePath;
		this.generateThumbnail = generateThumbnail;
		this.completed = false;
		this.generateThumbnail = false;
	}

	public boolean isCompleted() {
		return completed;
	}

	/**
	 * Mark the job as complete.
	 */
	public void markCompleted() {
		this.completed = true;
	}

	public UUID getJobId() {
		return jobId;
	}

	public String getAnchor() {
		return anchor;
	}

	public String getRelativePath() {
		return relativePath;
	}

	/**
	 * Should this job generate thumbnails?
	 * 
	 * @return returns true if thumbnails should be generated.
	 */
	public boolean isGenerateThumbnail() {
		return generateThumbnail;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof FileLoaderJob) {
			FileLoaderJob other = (FileLoaderJob) obj;

			return Objects.equals(this.getJobId(), other.getJobId());
		}

		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(getJobId());
	}
}
