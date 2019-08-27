package com.github.seeker.gui;

import java.io.IOException;

import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MetaDataExplorer extends Stage {
	private final MongoDbMapper mapper;
	
	public MetaDataExplorer(MongoDbMapper mapper) {
		this.mapper = mapper;
		
		
		ObservableList<ImageMetaData> ol = FXCollections.observableArrayList(mapper.getImageMetadata(100));
        ListView<ImageMetaData> l = new ListView<ImageMetaData>(ol);
        l.setCellFactory(new ImageMetaDataCellFactory());
		
		Scene scene = new Scene(new StackPane(l), 640, 480);
		setTitle("Metadata Exporer");
		setScene(scene);
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
