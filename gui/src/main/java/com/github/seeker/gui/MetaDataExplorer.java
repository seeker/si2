package com.github.seeker.gui;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.persistence.MinioPersistenceException;
import com.github.seeker.persistence.MinioStore;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;

import io.minio.errors.ErrorResponseException;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;

public class MetaDataExplorer extends Stage {
	private static final Logger LOGGER = LoggerFactory.getLogger(MetaDataExplorer.class);
	private final MongoDbMapper mapper;
	private final MinioStore minio;
	private final ImageView imageView;
	private final Pagination listPager;
	
	public MetaDataExplorer(MongoDbMapper mapper, MinioStore minio) throws IOException {
		this.mapper = mapper;
		this.minio = minio;
		
		ObservableList<ImageMetaData> ol = FXCollections.observableArrayList();
        TableView<ImageMetaData> table = new TableView<ImageMetaData>(ol);
        
        setUpTable(table);

        long metadataCount = mapper.getImageMetadataCount();
        LOGGER.debug("Database has {} metadata records", metadataCount);

        
        listPager = new Pagination();
        MetadataPageFactory pageFactory = new MetadataPageFactory(mapper,listPager, ol);
        listPager.setPageFactory(pageFactory);
        
        listenToChanges(table);
        
        pageFactory.updatePaginator();
		
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setVisible(true);
        
        HBox filterPane = createFilterPane(pageFactory);
        
        BorderPane border = new BorderPane();
        border.setTop(filterPane);
        border.setLeft(table);
        border.setCenter(imageView);
        border.setBottom(listPager);

		Scene scene = new Scene(border, 640, 480);
		setTitle("Metadata Exporer");
		setScene(scene);
	}
	

	
	private HBox createFilterPane(MetadataPageFactory pageFactory) {
		TextField anchorFilter = new TextField();
		TextField pathFilter = new TextField();
		Button filter = new Button("Filter");
		Button reload = new Button("Reload");
		
		EventHandler<ActionEvent> updateFilterAction = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				LOGGER.debug("User triggered metadata update");
			
				HashMap<String, Object> filterParameter = new HashMap<String, Object>();
				filterParameter.put("anchor", anchorFilter.getText());
				filterParameter.put("path", pathFilter.getText());

				pageFactory.setFilterParameters(filterParameter);
				pageFactory.updatePaginator();
			}
		}; 

		EventHandler<ActionEvent> reloadAction = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				LOGGER.debug("User triggered table reload");
				pageFactory.updatePaginator();
			}
		}; 
		
		filter.setOnAction(updateFilterAction);
		reload.setOnAction(reloadAction);
		anchorFilter.setOnAction(updateFilterAction);
		pathFilter.setOnAction(updateFilterAction);
		
		
		HBox filterPane = new HBox(anchorFilter, pathFilter, filter, reload);
		
		return filterPane;
	}



	private void setUpTable(TableView<ImageMetaData> table) {
        TableColumn<ImageMetaData, String> anchor = new TableColumn<ImageMetaData, String>("Anchor");
        TableColumn<ImageMetaData, String> relativePath = new TableColumn<ImageMetaData, String>("Relative Path");
        TableColumn<ImageMetaData, String> fileSize = new TableColumn<ImageMetaData, String>("File Size");
        
        anchor.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ImageMetaData,String>, ObservableValue<String>>() {
			@Override
			public ObservableValue<String> call(CellDataFeatures<ImageMetaData, String> param) {
				return new SimpleStringProperty(param.getValue().getAnchor());
			}
		});

        relativePath.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ImageMetaData,String>, ObservableValue<String>>() {
        	@Override
        	public ObservableValue<String> call(CellDataFeatures<ImageMetaData, String> param) {
        		return new SimpleStringProperty(param.getValue().getPath());
        	}
        });
        
        fileSize.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ImageMetaData,String>, ObservableValue<String>>() {
			@Override
			public ObservableValue<String> call(CellDataFeatures<ImageMetaData, String> param) {
				return new SimpleStringProperty(Long.toString(param.getValue().getFileSize()));
			}
		});
        
        
        table.getColumns().setAll(anchor, relativePath, fileSize);
	}
	
	private void listenToChanges(TableView<ImageMetaData> tableView) {
		tableView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<ImageMetaData>() {

			@Override
			public void changed(ObservableValue<? extends ImageMetaData> observable, ImageMetaData oldValue,
					ImageMetaData newValue) {
				if (newValue == null) {
					return;
				}
				
				if (!newValue.hasThumbnail()) {
					LOGGER.debug("No thumbnail available for {}:{}", newValue.getAnchor(), newValue.getPath());
					imageView.setImage(null);
					return;
				}
				
				LOGGER.debug("Selected {}:{}", newValue.getAnchor(), newValue.getPath());
				
				LOGGER.debug("Requesting thumbnail for image ID {}", newValue.getImageId());
				try {
					InputStream response = minio.getThumbnail(newValue.getImageId());

					Image image = new Image(response);
					imageView.setImage(image);
				} catch (MinioPersistenceException m) {
					if (m.getCause() instanceof ErrorResponseException) {
						Throwable ere = m.getCause();

						if ("NoSuchKey".equals(ere.getMessage())) {
							LOGGER.warn("Unable to find thumbnail for {} - {} (imageID {})", newValue.getAnchor(), newValue.getPath(), newValue.getImageId());
							imageView.setImage(null);
						} else {
							LOGGER.warn("Failed to load thumbnail: {}", ere.getMessage());
						}
					} else {
						LOGGER.error("Failed to load thumbnail: {}", m.getMessage());
					}

				}
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
