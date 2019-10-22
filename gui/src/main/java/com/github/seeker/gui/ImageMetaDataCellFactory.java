package com.github.seeker.gui;

import com.github.seeker.persistence.document.ImageMetaData;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;

public class ImageMetaDataCellFactory implements Callback<ListView<ImageMetaData>, ListCell<ImageMetaData>>{

	@Override
	public ListCell<ImageMetaData> call(ListView<ImageMetaData> param) {
		ListCell<ImageMetaData> cell = new ListCell<ImageMetaData>() {
			protected void updateItem(ImageMetaData item, boolean empty) {
				super.updateItem(item, empty);
			     if (empty || item == null) {
			         setText(null);
			         setGraphic(null);
			     } else {
			         setText(item.getAnchor() + ":" + item.getPath());
			     }
			}
		};
		
		return cell;
	}
}
