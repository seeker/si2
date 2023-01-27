package com.github.seeker.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bettercloud.vault.VaultException;
import com.github.seeker.configuration.ConnectionProvider;
import com.github.seeker.configuration.ConsulClient;
import com.github.seeker.configuration.FileLoaderConfiguration;
import com.github.seeker.configuration.QueueConfiguration;
import com.github.seeker.configuration.QueueConfiguration.ConfiguredExchanges;
import com.github.seeker.configuration.RabbitMqRole;
import com.github.seeker.messaging.proto.NodeCommandOuterClass.LoaderCommand;
import com.github.seeker.messaging.proto.NodeCommandOuterClass.NodeCommand;
import com.github.seeker.messaging.proto.NodeCommandOuterClass.NodeType;
import com.github.seeker.persistence.MinioStore;
import com.github.seeker.persistence.MongoDbMapper;
import com.github.seeker.persistence.document.FileLoaderJob;
import com.github.seeker.processor.FileToQueueVistor;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Loads files from the file system and sends them to the message broker with additional meta data.
 */
public class FileLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileLoader.class);
	
	private final Channel channel;
	private final MongoDbMapper mapper;
	private final MinioStore minio;
	private final List<String> requriedHashes;
	private final QueueConfiguration queueConfig;
	
	private FileToQueueVistor fileToQueueVistor;
	private final AtomicBoolean walking;
	private final FileLoaderConfiguration fileLoaderConfig;
	
	public static enum Command {
		/**
		 * Stop any in progress file walks.
		 */
		stop,
		/**
		 * Start file walking.
		 */
		start
	}
	
	//TODO get file types from consul
	
	public FileLoader(String id, ConnectionProvider connectionProvider, FileLoaderConfiguration fileLoaderConfig,
			MinioStore minio) throws IOException, TimeoutException, VaultException {
		this.walking = new AtomicBoolean();
		this.fileLoaderConfig = fileLoaderConfig;
		
		ConsulClient consul = connectionProvider.getConsulClient();
		Connection conn = connectionProvider.getRabbitMQConnectionFactory(RabbitMqRole.file_loader).newConnection();
		channel = conn.createChannel();
		queueConfig = new QueueConfiguration(channel);
		
		mapper = connectionProvider.getMongoDbMapper();
		this.minio = minio;

		requriedHashes = Arrays.asList(consul.getKvAsString("config/general/required-hashes").split(Pattern.quote(",")));
		
		LOGGER.info("Loaded anchors from config:\n {}", fileLoaderConfig.anchors());
		
		LOGGER.info("Setting up command queue...");
		String queue = channel.queueDeclare().getQueue();
		channel.queueBind(queue, queueConfig.getExchangeName(ConfiguredExchanges.loaderCommand), "");
		channel.basicConsume(queue, true, new FileLoaderCommandConsumer(channel, this));
		
		LOGGER.info("Ready and waiting for commands.");
		
		try {
			synchronized(this) {
			    while (true) {
			        this.wait();
			    }
			}
		} catch (InterruptedException e) {
			LOGGER.info("Was interrupted from wait call.");
		}
	}
	
	public void loadFiles() {
		Map<String, String> anchors = fileLoaderConfig.anchors();
		
		for (Entry<String, String> entry : anchors.entrySet()) {
			String anchor = entry.getKey();
			LOGGER.info("Processing loading jobs for {}", anchor);
			
			while(true) {
				if(!walking.get()) {
					LOGGER.info("File walk interrupted, aborting...");
					break;
				}
				
				FileLoaderJob job = mapper.getOpenFileLoadJobsForAnchor(anchor);
				
				if(Objects.isNull(job)) {
					break;
				}
				LOGGER.info("Working on Job ID {}, {}-{}", job.getJobId(), job.getAnchor(), job.getRelativePath());
				Path anchorAbsolutePath = Paths.get(anchors.get(anchor), job.getRelativePath());
				
				loadFilesForAnchor(anchor, anchorAbsolutePath, Paths.get(anchors.get(anchor)), job.isGenerateThumbnail());
				job.markCompleted();
				mapper.storeFileLoadJob(job);
			}
			
		}
		
		LOGGER.info("Finished processing Jobs, waiting for more work...");
		walking.set(false);
	}
	
	private void loadFilesForAnchor(String anchor, Path anchorAbsolutePath, Path anchorRootPath, boolean generateThumbnails) {
		LOGGER.info("Walking {} for anchor {}", anchorRootPath, anchor);
		
		fileToQueueVistor = new FileToQueueVistor(channel, anchor, anchorRootPath, mapper, minio, requriedHashes,
				queueConfig.getExchangeName(ConfiguredExchanges.loader));
		fileToQueueVistor.setGenerateThumbnails(generateThumbnails);
		
		try {
			Files.walkFileTree(anchorAbsolutePath, fileToQueueVistor);
		} catch (IOException e) {
			LOGGER.warn("Failed to walk file tree for {}: {}", anchorAbsolutePath, e.getMessage());
		}
	}
	
	protected void stopFileWalk() {
		LOGGER.info("Terminating active file walk...");
		if(Objects.nonNull(fileToQueueVistor)) {
			fileToQueueVistor.terminate();
		}
		
		walking.set(false);
	}
	
	protected void startFileWalk() {
		if(walking.get()) {
			LOGGER.info("Already walking, ignoring request...");
			return;
		}
		
		walking.set(true);
		
		loadFiles();
	}
}

class FileLoaderCommandConsumer extends DefaultConsumer {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileLoaderCommandConsumer.class);

	private final FileLoader parent;

	public FileLoaderCommandConsumer(Channel channel, FileLoader parent) {
		super(channel);

		this.parent = parent;
	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
			throws IOException {
		NodeCommand message = NodeCommand.parseFrom(body);
		
		if (NodeType.NODE_TYPE_LOADER.equals(message.getNodeType())) {
			LoaderCommand command = message.getLoaderCommand();

			switch (command) {
			case LOADER_COMMAND_START:
				LOGGER.info("Received start command, starting file walk...");
				parent.startFileWalk();
				break;
			case LOADER_COMMAND_STOP:
				LOGGER.info("Received stop command, terminating in progress file walk...");
				parent.stopFileWalk();
				break;
			default:
				LOGGER.warn("Received a message with an unhandled loader command: {}", command);
				break;
			}
		}
	}
}
