package com.github.seeker.gui;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.FileLoaderJob;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;

public class FileLoaderJobs extends Stage {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileLoaderJobs.class);
	private final MongoDbMapper mapper;
	private final ObservableList<FileLoaderJob> jobList;

	public FileLoaderJobs(MongoDbMapper mapper) throws IOException {
		this.mapper = mapper;

		jobList = FXCollections.observableArrayList();
		TableView<FileLoaderJob> table = new TableView<FileLoaderJob>(jobList);

		setUpTable(table);

		long metadataCount = mapper.getImageMetadataCount();
		LOGGER.debug("Database has {} metadata records", metadataCount);

		listenToChanges(table);

		BorderPane border = new BorderPane();
		border.setCenter(table);
		border.setBottom(createJobEntryPane());

		Scene scene = new Scene(border, 640, 480);
		setTitle("FileLoader Jobs");
		setScene(scene);

		refreshJobList();
	}

	private void refreshJobList() {
		jobList.clear();
		jobList.addAll(mapper.getAllFileLoadJobs());
	}

	private HBox createJobEntryPane() {
		TextField anchor = new TextField();
		TextField relativePath = new TextField();
		CheckBox thumbnails = new CheckBox("Gen. Thumbnails");
		
		Button submit = new Button("Submit");
		Button refresh = new Button("Refresh");

		EventHandler<ActionEvent> storeJob = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				FileLoaderJob job = new FileLoaderJob(anchor.getText(), relativePath.getText(), thumbnails.isSelected());
				mapper.storeFileLoadJob(job);
				refreshJobList();
			}
		};

		EventHandler<ActionEvent> refreshJobs = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				refreshJobList();
			}
		};

		submit.setOnAction(storeJob);
		refresh.setOnAction(refreshJobs);
		HBox filterPane = new HBox(anchor, relativePath, thumbnails, submit, refresh);

		return filterPane;
	}

	private void setUpTable(TableView<FileLoaderJob> table) {
		TableColumn<FileLoaderJob, String> jobId = new TableColumn<FileLoaderJob, String>("Job ID");
		TableColumn<FileLoaderJob, String> anchor = new TableColumn<FileLoaderJob, String>("Anchor");
		TableColumn<FileLoaderJob, String> relativePath = new TableColumn<FileLoaderJob, String>("Relative Path");
		TableColumn<FileLoaderJob, String> generateThumb = new TableColumn<FileLoaderJob, String>("Generate Thumb");
		TableColumn<FileLoaderJob, String> completed = new TableColumn<FileLoaderJob, String>("Completed");

		jobId.setCellValueFactory(
				new Callback<TableColumn.CellDataFeatures<FileLoaderJob, String>, ObservableValue<String>>() {
					@Override
					public ObservableValue<String> call(CellDataFeatures<FileLoaderJob, String> param) {
						return new SimpleStringProperty(param.getValue().getJobId().toString());
					}
				});

		anchor.setCellValueFactory(
				new Callback<TableColumn.CellDataFeatures<FileLoaderJob, String>, ObservableValue<String>>() {
					@Override
					public ObservableValue<String> call(CellDataFeatures<FileLoaderJob, String> param) {
						return new SimpleStringProperty(param.getValue().getAnchor());
					}
				});

		relativePath.setCellValueFactory(
				new Callback<TableColumn.CellDataFeatures<FileLoaderJob, String>, ObservableValue<String>>() {
					@Override
					public ObservableValue<String> call(CellDataFeatures<FileLoaderJob, String> param) {
						return new SimpleStringProperty(param.getValue().getRelativePath());
					}
				});

		generateThumb.setCellValueFactory(
				new Callback<TableColumn.CellDataFeatures<FileLoaderJob, String>, ObservableValue<String>>() {
					@Override
					public ObservableValue<String> call(CellDataFeatures<FileLoaderJob, String> param) {
						return new SimpleStringProperty(Boolean.toString(param.getValue().isGenerateThumbnail()));
					}
				});

		completed.setCellValueFactory(
				new Callback<TableColumn.CellDataFeatures<FileLoaderJob, String>, ObservableValue<String>>() {
					@Override
					public ObservableValue<String> call(CellDataFeatures<FileLoaderJob, String> param) {
						return new SimpleStringProperty(Boolean.toString(param.getValue().isCompleted()));
					}
				});

		table.getColumns().setAll(Arrays.asList(jobId, anchor, relativePath, generateThumb, completed));
	}

	private void listenToChanges(TableView<FileLoaderJob> tableView) {
		tableView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<FileLoaderJob>() {

			@Override
			public void changed(ObservableValue<? extends FileLoaderJob> observable, FileLoaderJob oldValue,
					FileLoaderJob newValue) {
				if (newValue == null) {
					return;
				}
			}
		});
	}
}
