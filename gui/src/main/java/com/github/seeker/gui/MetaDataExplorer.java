package com.github.seeker.gui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredQueues;
import com.github.seeker.messaging.MessageHeaderKeys;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.ImageMetaData;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Callback;

public class MetaDataExplorer extends Stage {
	private static final Logger LOGGER = LoggerFactory.getLogger(MetaDataExplorer.class);
	private final MongoDbMapper mapper;
	private final Channel channel;
	private final String replyQueue;
	private final QueueConfiguration queueConfig;
	private final ImageView imageView;
	private final Pagination listPager;
	
	public MetaDataExplorer(MongoDbMapper mapper, Connection rabbitConn, QueueConfiguration queueConfig) throws IOException {
		this.mapper = mapper;
		this.queueConfig = queueConfig;
		
		this.channel = rabbitConn.createChannel();
		this.replyQueue = channel.queueDeclare().getQueue();
		
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
        imageView.setFitWidth(200);
        imageView.setFitHeight(200);
        imageView.setPreserveRatio(true);
        imageView.setVisible(true);
        BorderPane border = new BorderPane();
        border.setLeft(table);
        border.setCenter(imageView);
        border.setBottom(listPager);
        
		Scene scene = new Scene(border, 640, 480);
		setTitle("Metadata Exporer");
		setScene(scene);
		
		channel.basicConsume(replyQueue, true, new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
				String thumbnailFound = properties.getHeaders().get(MessageHeaderKeys.THUMBNAIL_FOUND).toString();
				
				if(! Boolean.parseBoolean(thumbnailFound)) {
					LOGGER.warn("No thumbnail found for image");
					return;
				}
				
				ByteArrayInputStream bais = new ByteArrayInputStream(body);
				Image image = new Image(bais);
				imageView.setImage(image);
				
				LOGGER.debug("Consumed message with correlation ID {}", properties.getCorrelationId());
			}
		});
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
				
				if(! newValue.hasThumbnail()) {
					LOGGER.warn("No thumbnail available for {}:{}", newValue.getAnchor(), newValue.getPath());
					return;
				}
				
				LOGGER.debug("Selected {}:{}", newValue.getAnchor(), newValue.getPath());
				
				final String correlationId = UUID.randomUUID().toString();
				
				AMQP.BasicProperties props = new AMQP.BasicProperties().builder().correlationId(correlationId).replyTo(replyQueue).build();
				
				UUID thumbnailID = newValue.getThumbnailId();
				
				ByteArrayDataOutput data = ByteStreams.newDataOutput(16);
				data.writeLong(thumbnailID.getMostSignificantBits());
				data.writeLong(thumbnailID.getLeastSignificantBits());
				
				byte[] raw = data.toByteArray();
				
				try {
					LOGGER.debug("Requesting thumbnail with ID {}", thumbnailID);
					channel.basicPublish("", queueConfig.getQueueName(ConfiguredQueues.thumbnailRequests), props, raw);
				} catch (IOException e) {
					LOGGER.warn("Failed to send thumbnail request for {}: {}", newValue.getThumbnailId(), e);
					e.printStackTrace();
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
