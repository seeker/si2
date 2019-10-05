package com.github.seeker.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MetaDataExplorer extends Stage {
	private static final Logger LOGGER = LoggerFactory.getLogger(MetaDataExplorer.class);
	private final MongoDbMapper mapper;
	
	public MetaDataExplorer(MongoDbMapper mapper) {
		this.mapper = mapper;
		
		
		ObservableList<ImageMetaData> ol = FXCollections.observableArrayList(mapper.getImageMetadata(100));
        ListView<ImageMetaData> l = new ListView<ImageMetaData>(ol);
        l.setCellFactory(new ImageMetaDataCellFactory());
        listenToChanges(l);
		
		Scene scene = new Scene(new StackPane(l), 640, 480);
		setTitle("Metadata Exporer");
		setScene(scene);
	}
	
	private void listenToChanges(ListView<ImageMetaData> listView) {
		listView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<ImageMetaData>() {

			@Override
			public void changed(ObservableValue<? extends ImageMetaData> observable, ImageMetaData oldValue,
					ImageMetaData newValue) {
				LOGGER.debug("Selected {}:{}", newValue.getAnchor(), newValue.getPath());
				
			}
		});
	}
	
	/*						|
	 *						|
	 * LSIT OF METADATA   	|		Thumbnail	
	 * 						|
	 * 						|
	 * ---------------------------------------------------------
	 * 
	 * Anchor filter field | Path filter field | tag filter field?  
	 * 
	 * 
	 */
}
