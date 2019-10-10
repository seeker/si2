package com.github.seeker.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

public class MetadataPageFactory implements Callback<Integer, Node>{
	private static final Logger LOGGER = LoggerFactory.getLogger(MetadataPageFactory.class);
	
	private static final int ENTRIES_PER_PAGE = 100;
	private final MongoDbMapper mapper;
	private final ObservableList<ImageMetaData> list;
	
	public MetadataPageFactory(MongoDbMapper mapper, ObservableList<ImageMetaData> list) {
		this.mapper = mapper;
		this.list = list;
	}

	@Override
	public Node call(Integer paginationIndex) {
		LOGGER.debug("Setting list contents for page index {}", paginationIndex);
		
		list.clear();
		list.addAll(mapper.getImageMetadata(paginationIndex*ENTRIES_PER_PAGE, ENTRIES_PER_PAGE));
		LOGGER.debug("Loaded {} entries...", list.size());
		
		return new VBox();
	}

}
