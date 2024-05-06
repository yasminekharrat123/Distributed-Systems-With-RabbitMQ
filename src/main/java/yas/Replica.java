package yas;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class Replica {
    private static Connection connection;
    private static Channel channel;
    private static final String WRITE_EXCHANGE_NAME = "WriterEXCHANGE";
    private static final String READ_REQUEST_EXCHANGE_NAME = "ReaderEXCHANGE";
    private static final String DELIVERY_EXCHANGE_NAME = "DeliveryEXCHANGE";
    private static String replicaNumber;
    private static String fileName;
    private static final String FILES_FOLDER_PATH = "src/main/java/yas/files";

    private static FileService fileService;

    public static void main(String[] args) {
        // Verify argument for replica number
        if (args.length != 1) {
            System.out.println("You must enter a replica number.");
            System.exit(1);
        }
        replicaNumber = args[0];
        setupFileService();
        System.out.println("Replica " + replicaNumber + " started");

        // Set up RabbitMQ connection and channel
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.basicQos(10);

            // Initialize write and read processes
            String queueWriter = setupWritingRequest(channel);
            String queueReader = setupReadingRequest(channel);

            // Start consuming messages
            startServer(channel, queueWriter, queueReader);
        } catch (IOException | TimeoutException e) {
            System.err.println("Error setting up RabbitMQ connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Sets up and starts the message consumption loops
    private static void startServer(Channel channel, String queueWriter, String queueReader) throws IOException {
        // Write request callback
        DeliverCallback writeCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received from Writer Client: " + message);
            try {
                fileService.writeToFile(message);
            } catch (Exception e) {
                System.err.println("Error writing to file: " + e.getMessage());
                e.printStackTrace();
            }
        };

        // Read request callback
        DeliverCallback readCallback = (consumerTag, delivery) -> {
            String request = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received request from Reader Client: " + request);

            try {
                if ("Read Last".equals(request)) {
                    String lastMessage = fileService.readLastLine();
                    sendMessageToReader(lastMessage, channel);
                } else if ("Read All".equals(request)) {
                    List<String> allLines = fileService.readAllLines();
                    for (String line : allLines) {
                        sendMessageToReader(line, channel);
                    }
                    sendMessageToReader("data all sent", channel);
                }
            } catch (IOException e) {
                System.err.println("Error processing read request: " + e.getMessage());
                e.printStackTrace();
            }
        };

        // Start consuming messages with defined callbacks
        channel.basicConsume(queueWriter, true, writeCallback, consumerTag -> {});
        channel.basicConsume(queueReader, true, readCallback, consumerTag -> {});
    }



    // Initialize the writing process with RabbitMQ
    private static String setupWritingRequest(Channel channel) throws IOException {
        channel.exchangeDeclare(WRITE_EXCHANGE_NAME, "fanout");
        String queueWriter = channel.queueDeclare().getQueue();
        channel.queueBind(queueWriter, WRITE_EXCHANGE_NAME, "");
        System.out.println(" [*] Waiting for messages from Writer. To exit, press CTRL+C");
        return queueWriter;
    }

    // Initialize file service with replica-specific filename
    private static void setupFileService() {
        fileService = new FileService(FILES_FOLDER_PATH + "/" + "replica" + replicaNumber + ".txt");
    }

    // Initialize the reading process with RabbitMQ
    private static String setupReadingRequest(Channel channel) throws IOException {
        channel.exchangeDeclare(READ_REQUEST_EXCHANGE_NAME, "fanout");
        String queueReader = channel.queueDeclare().getQueue();
        channel.queueBind(queueReader, READ_REQUEST_EXCHANGE_NAME, "");
        System.out.println(" [*] Waiting for Requests. To exit, press CTRL+C");
        return queueReader;
    }

    // Send message to Reader client via RabbitMQ
    private static void sendMessageToReader(String message, Channel channel) throws IOException {
        channel.exchangeDeclare(DELIVERY_EXCHANGE_NAME, "fanout");
        channel.basicPublish(DELIVERY_EXCHANGE_NAME, "", null, message.getBytes(StandardCharsets.UTF_8));
        System.out.println(" [x] Sent to Reader Client: " + message);
    }
}
