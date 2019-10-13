package com.github.seeker.gui;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Pagination;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

public class MetadataPageFactory implements Callback<Integer, Node>{
	private static final Logger LOGGER = LoggerFactory.getLogger(MetadataPageFactory.class);
	
	private static final int ENTRIES_PER_PAGE = 100;
	private final MongoDbMapper mapper;
	private final ObservableList<ImageMetaData> list;
	private final Pagination parent;
	private HashMap<String, Object> filterParameters = new HashMap<String, Object>();
	
	public MetadataPageFactory(MongoDbMapper mapper,Pagination parent, ObservableList<ImageMetaData> list) {
		this.mapper = mapper;
		this.list = list;
		this.parent = parent;
	}

	@Override
	public Node call(Integer paginationIndex) {
		LOGGER.debug("Setting list contents for page index {}", paginationIndex);
		
		list.clear();
		list.addAll(mapper.getImageMetadata(paginationIndex*ENTRIES_PER_PAGE, ENTRIES_PER_PAGE));
		LOGGER.debug("Loaded {} entries...", list.size());
		
		return new VBox();
	}

	public HashMap<String, Object> getFilterParameters() {
		return filterParameters;
	}

	public void setFilterParameters(HashMap<String, Object> filterParameters) {
		this.filterParameters = filterParameters;
	}

	/**
	 * Query the database for results using the stored filter parameters and update the pagination.
	 */
	public void updatePaginator() {
		LOGGER.debug("Updating paginator...");
		updatePageCount();
		call(0);
	}
	
	private void updatePageCount() {
		long metadataCount = mapper.getFilteredImageMetadataCount(filterParameters);
		int numberOfPages = numberOfPages(metadataCount);

		parent.setPageCount(numberOfPages);
		
		LOGGER.debug("Set paginator page count to {}", numberOfPages);
	}
	
	private int numberOfPages(long metadataCount) {
		return (int)((metadataCount / 100) + 1);
	}
}
