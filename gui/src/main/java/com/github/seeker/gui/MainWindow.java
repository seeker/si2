package com.github.seeker.gui;

import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.persistence.MongoDbMapper;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainWindow extends Application{
	private MongoDbMapper mapper;
	private MetaDataExplorer metaDataExplorer;
	
	public static void main(String[] args) {
		launch(args);
	}

	private void setUpVars() {
		ConnectionProvider connectionProvider = new ConnectionProvider(new ConfigurationBuilder().getConsulConfiguration());
		mapper = connectionProvider.getMongoDbMapper();
		metaDataExplorer = new MetaDataExplorer(mapper);
	}
	
	private MenuBar buildMenuBar() {
		MenuBar menuBar = new MenuBar();
		
		Menu actions = new Menu("Actions");
		MenuItem exploreMetaData = new MenuItem("Explore MetaData");
		exploreMetaData.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				metaDataExplorer.show();
			}
		});
		
		actions.getItems().add(exploreMetaData);
		
		menuBar.getMenus().add(actions);
		
		return menuBar;
	}
	
	@Override
	public void start(Stage primaryStage) throws Exception {
		setUpVars();
		
		ObservableList<String> ol = FXCollections.observableArrayList("foo", "bar", "baz");
		
        ListView<String> l = new ListView<String>(ol);
        BorderPane bp = new BorderPane();
        
        bp.setTop(buildMenuBar());
        bp.setCenter(new StackPane(l));
        Scene scene = new Scene(bp, 640, 480);

        primaryStage.setScene(scene);
        primaryStage.show();		
	}
}
