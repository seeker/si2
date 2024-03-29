package com.github.seeker.gui;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.FileLoaderJob;

import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
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
	
	private void persistJobList() {
		jobList.forEach(job -> mapper.storeFileLoadJob(job));
	}

	private HBox createJobEntryPane() {
		TextField anchor = new TextField();
		anchor.setTooltip(new Tooltip("Anchor"));

		TextField relativePath = new TextField();
		relativePath.setTooltip(new Tooltip("Relative path to anchor"));

		CheckBox thumbnails = new CheckBox("Gen. Thumbnails");

		Button submit = new Button("Add");
		Button refresh = new Button("Refresh");
		Button persist = new Button("Persist table");
		persist.setTooltip(new Tooltip("Persist the currently displayed table to the datanase"));

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
		
		EventHandler<ActionEvent> persistJobs = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				persistJobList();
			}
		};
		
		

		submit.setOnAction(storeJob);
		refresh.setOnAction(refreshJobs);
		persist.setOnAction(persistJobs);
		HBox filterPane = new HBox(anchor, relativePath, thumbnails, submit, refresh, persist);

		return filterPane;
	}

	private void setUpTable(TableView<FileLoaderJob> table) {
		table.setEditable(true);

		TableColumn<FileLoaderJob, String> jobId = new TableColumn<FileLoaderJob, String>("Job ID");
		TableColumn<FileLoaderJob, String> anchor = new TableColumn<FileLoaderJob, String>("Anchor");
		TableColumn<FileLoaderJob, String> relativePath = new TableColumn<FileLoaderJob, String>("Relative Path");
		TableColumn<FileLoaderJob, Boolean> generateThumb = new TableColumn<FileLoaderJob, Boolean>("Generate Thumb");
		TableColumn<FileLoaderJob, Boolean> completed = new TableColumn<FileLoaderJob, Boolean>("Completed");

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
				new Callback<TableColumn.CellDataFeatures<FileLoaderJob, Boolean>, ObservableValue<Boolean>>() {
					@Override
					public ObservableValue<Boolean> call(CellDataFeatures<FileLoaderJob, Boolean> param) {
						SimpleBooleanProperty sbp = new SimpleBooleanProperty(param.getValue().isGenerateThumbnail());
						
						sbp.addListener(new ChangeListener<Boolean>() {
							@Override
							public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue,
									Boolean newValue) {
								param.getValue().setGenerateThumbnail(newValue);
							}
						});
						
						return sbp;
					}
				});

		generateThumb.setCellFactory(new Callback<TableColumn<FileLoaderJob,Boolean>, TableCell<FileLoaderJob,Boolean>>() {
			@Override
			public TableCell<FileLoaderJob, Boolean> call(TableColumn<FileLoaderJob, Boolean> param) {
				return new CheckBoxTableCell<FileLoaderJob, Boolean>();
			}
		});

		completed.setCellValueFactory(
				new Callback<TableColumn.CellDataFeatures<FileLoaderJob, Boolean>, ObservableValue<Boolean>>() {
					@Override
					public ObservableValue<Boolean> call(CellDataFeatures<FileLoaderJob, Boolean> param) {
						SimpleBooleanProperty sbp = new SimpleBooleanProperty(param.getValue().isCompleted());
						
						sbp.addListener(new ChangeListener<Boolean>() {
							@Override
							public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue,
									Boolean newValue) {
								param.getValue().setCompleted(newValue);
							}
						});
						
						return sbp;
					}
				});
		
		completed.setCellFactory(new Callback<TableColumn<FileLoaderJob,Boolean>, TableCell<FileLoaderJob,Boolean>>() {
			@Override
			public TableCell<FileLoaderJob, Boolean> call(TableColumn<FileLoaderJob, Boolean> param) {
				return new CheckBoxTableCell<FileLoaderJob, Boolean>();
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
