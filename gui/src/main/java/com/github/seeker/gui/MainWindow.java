package com.github.seeker.gui;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.VaultException;
import com.github.seeker.configuration.ConfigurationBuilder;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.ConsulConfiguration;
import com.github.seeker.configuration.MinioConfiguration;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredExchanges;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.messaging.UUIDUtils;
import com.github.seeker.persistence.MinioStore;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import de.caluga.morphium.query.MorphiumIterator;
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
	private MinioStore minio;
	private MetaDataExplorer metaDataExplorer;
	private FileLoaderJobs fileLoaderJobs;
	private QueueConfiguration queueConfig;
	private ConsulClient consul;
	
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
		
		queueConfig = new QueueConfiguration(rabbitConnection.createChannel());
		
		mapper = connectionProvider.getMongoDbMapper();
		minio = new MinioStore(connectionProvider.getMinioClient(), MinioConfiguration.productionBuckets());
		consul = connectionProvider.getConsulClient();

		metaDataExplorer = new MetaDataExplorer(mapper, minio);
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
		
		MenuItem recreateThumbnails = new MenuItem("Recreate thumbnails");
		recreateThumbnails.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				recreateThumbnails();
			}
		});

		MenuItem pruneProcessedImages = new MenuItem("Prune processed images");
		pruneProcessedImages.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				MorphiumIterator<ImageMetaData> originalImagesToDelete = mapper
						.getProcessingCompletedMetadata(Arrays.asList(consul.getKvAsString("config/general/required-hashes").split(Pattern.quote(","))));
				
				LOGGER.info("Found {} complete metadata entries", originalImagesToDelete.getCount());
				
				minio.deleteImages(originalImagesToDelete);
			}
		});

		actions.getItems().add(exploreMetaData);
		actions.getItems().add(viewFileLoaderJobs);
		actions.getItems().add(startLoader);
		actions.getItems().add(stoploader);
		actions.getItems().add(pruneThumbs);
		actions.getItems().add(recreateThumbnails);
		actions.getItems().add(pruneProcessedImages);
		
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
	
	private void recreateThumbnails() {


		int thumbnailSize = (int)consul.getKvAsLong("config/general/thumbnail-size");
		MorphiumIterator<ImageMetaData> iter = mapper.getThumbnailsToResize(thumbnailSize);
		
		LOGGER.info("Queueing thumbnail generation for {} thumbnails which do not have the required size of {}", iter.getCount(), thumbnailSize);
		
		for (ImageMetaData meta : iter) {
			try {
				Map<String, Object> headers = new HashMap<String, Object>();
				headers.put(MessageHeaderKeys.THUMBNAIL_RECREATE, "");
				headers.put(MessageHeaderKeys.ANCHOR, meta.getAnchor());
				headers.put(MessageHeaderKeys.ANCHOR_RELATIVE_PATH, meta.getPath());
				AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();

				channel.basicPublish(queueConfig.getExchangeName(ConfiguredExchanges.loader), "", props, UUIDUtils.UUIDtoByte(meta.getImageId()));
			} catch (IOException e) {
				LOGGER.warn("Failed to create thumbnail recreate message for {} - {} due to {}", meta.getAnchor(), meta.getPath(), e.getMessage());
			}
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
