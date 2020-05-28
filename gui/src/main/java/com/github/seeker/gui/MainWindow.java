package com.github.seeker.gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.VaultException;
import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredExchanges;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.persistence.MongoDbMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

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
	private static final Logger LOGGER = LoggerFactory.getLogger(MainWindow.class); 
	
	private MongoDbMapper mapper;
	private MetaDataExplorer metaDataExplorer;
	private FileLoaderJobs fileLoaderJobs;
	private QueueConfiguration queueConfig;
	
	private Channel channel;
	
	public static void main(String[] args) {
		launch(args);
	}

	private void setUpVars() throws IOException, TimeoutException, VaultException {
		ConfigurationBuilder configBuilder = new ConfigurationBuilder();
		ConsulConfiguration consulConfig = configBuilder.getConsulConfiguration();
		
		ConnectionProvider connectionProvider = new ConnectionProvider(consulConfig, configBuilder.getVaultCredentials(), consulConfig.overrideVirtualBoxAddress());
		Connection rabbitConnection = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.dbnode).newConnection();
		this.channel = rabbitConnection.createChannel();
		
		queueConfig = new QueueConfiguration(rabbitConnection.createChannel(), connectionProvider.getConsulClient());
		
		mapper = connectionProvider.getMongoDbMapper();
		metaDataExplorer = new MetaDataExplorer(mapper, rabbitConnection, queueConfig);
		fileLoaderJobs = new FileLoaderJobs(mapper);
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

		MenuItem viewFileLoaderJobs = new MenuItem("File loader jobs");
		viewFileLoaderJobs.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				fileLoaderJobs.show();
			}
		});
		
		MenuItem startLoader = new MenuItem("Start Loader");
		startLoader.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				sendLoaderCommand("start");
			}
		});
		
		MenuItem stoploader = new MenuItem("Stop loader");
		stoploader.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				sendLoaderCommand("stop");
			}
		});
		
		MenuItem pruneThumbs = new MenuItem("Prune thumbs");
		pruneThumbs.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				sendThumbnailCommand("prune_thumbnails");
			}
		});
		
		actions.getItems().add(exploreMetaData);
		actions.getItems().add(viewFileLoaderJobs);
		actions.getItems().add(startLoader);
		actions.getItems().add(stoploader);
		actions.getItems().add(pruneThumbs);
		
		menuBar.getMenus().add(actions);
		
		return menuBar;
	}
	
	private void sendLoaderCommand(String command) {
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(MessageHeaderKeys.FILE_LOADER_COMMAND, command);
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();
		
		try {
			LOGGER.info("Sending {} command to file loaders", command);
			channel.basicPublish(queueConfig.getExchangeName(ConfiguredExchanges.loaderCommand), "", props, null);
		} catch (IOException e) {
			LOGGER.error("Failed to send file loader command {}", command, e);
		}
	}
	
	private void sendThumbnailCommand(String command) {
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(MessageHeaderKeys.THUMB_NODE_COMMAND, command);
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();
		
		try {
			LOGGER.info("Sending {} command to nodes loaders", command);
			channel.basicPublish(queueConfig.getExchangeName(ConfiguredExchanges.loaderCommand), "", props, null);
		} catch (IOException e) {
			LOGGER.error("Failed to send node command {}", command, e);
		}
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
