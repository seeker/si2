package com.github.seeker.gui;

import java.io.IOException;
import java.util.Arrays;
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
import com.github.seeker.messaging.proto.FileLoadOuterClass.FileLoad;
import com.github.seeker.messaging.proto.NodeCommandOuterClass.LoaderCommand;
import com.github.seeker.messaging.proto.NodeCommandOuterClass.NodeCommand;
import com.github.seeker.messaging.proto.NodeCommandOuterClass.NodeType;
import com.github.seeker.persistence.MinioStore;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import de.caluga.morphium.query.QueryIterator;
import javafx.application.Application;
import javafx.application.Platform;
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
	private ConnectionProvider connectionProvider;
	private Connection rabbitConnection;
	private Channel channel;
	
	public static void main(String[] args) {
		launch(args);
	}

	private void setUpVars() throws IOException, TimeoutException, VaultException {
		ConfigurationBuilder configBuilder = new ConfigurationBuilder();
		ConsulConfiguration consulConfig = configBuilder.getConsulConfiguration();
		
		connectionProvider = new ConnectionProvider(consulConfig, configBuilder.getVaultCredentials(), consulConfig.overrideVirtualBoxAddress());
		rabbitConnection = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.dbnode).newConnection();
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
				sendLoaderCommand(LoaderCommand.LOADER_COMMAND_START);
			}
		});
		
		MenuItem stoploader = new MenuItem("Stop loader");
		stoploader.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				sendLoaderCommand(LoaderCommand.LOADER_COMMAND_STOP);
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
				QueryIterator<ImageMetaData> originalImagesToDelete = (QueryIterator<ImageMetaData>) mapper
						.getProcessingCompletedMetadata(Arrays.asList(consul.getKvAsString("config/general/required-hashes").split(Pattern.quote(","))));

				LOGGER.info("Found {} complete metadata entries", originalImagesToDelete.getCount());
				
				minio.deleteImages(originalImagesToDelete);
			}
		});

		actions.getItems().add(exploreMetaData);
		actions.getItems().add(viewFileLoaderJobs);
		actions.getItems().add(startLoader);
		actions.getItems().add(stoploader);
		actions.getItems().add(recreateThumbnails);
		actions.getItems().add(pruneProcessedImages);
		
		menuBar.getMenus().add(actions);
		
		return menuBar;
	}

	private void sendLoaderCommand(LoaderCommand command) {
		try {
			LOGGER.info("Sending {} command to file loaders", command);
			NodeCommand message = NodeCommand.newBuilder().setNodeType(NodeType.NODE_TYPE_LOADER).setLoaderCommand(command).build();

			channel.basicPublish(queueConfig.getExchangeName(ConfiguredExchanges.loaderCommand), "", null, message.toByteArray());
		} catch (IOException e) {
			LOGGER.error("Failed to send file loader command {}", command, e);
		}
	}
	
	private void recreateThumbnails() {


		int thumbnailSize = (int)consul.getKvAsLong("config/general/thumbnail-size");
		QueryIterator<ImageMetaData> iter = (QueryIterator<ImageMetaData>) mapper.getThumbnailsToResize(thumbnailSize);

		LOGGER.info("Queueing thumbnail generation for {} thumbnails which do not have the required size of {}", iter.getCount(), thumbnailSize);
		
		for (ImageMetaData meta : iter) {
			try {
				FileLoad.Builder builder = FileLoad.newBuilder();
				builder.getImagePathBuilder().setAnchor(meta.getAnchor()).setRelativePath(meta.getPath());
				builder.setImageId(meta.getImageId().toString());
				builder.setRecreateThumbnail(true);

				channel.basicPublish(queueConfig.getExchangeName(ConfiguredExchanges.loader), "", null, builder.build().toByteArray());
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

	@Override
	public void stop() throws Exception {
		super.stop();
		LOGGER.info("JavaFx stop method called");

		this.rabbitConnection.close();
		this.connectionProvider.shutdown();

		Platform.exit();
	}
}
