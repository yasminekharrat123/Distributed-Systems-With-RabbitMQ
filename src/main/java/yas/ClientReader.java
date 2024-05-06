package yas;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

public class ClientReader {
    private static final String READ_REQUEST_EXCHANGE_NAME = "ReaderEXCHANGE";
    private static final String DELIVERY_EXCHANGE_NAME = "DeliveryEXCHANGE";
    private static final int NUM_REPLICAS = 3;
    private static Connection connection;
    private static Channel channel;

    public static void main(String[] args) {
        setupConnectionAndChannel();
        String queueDelivery = setupDeliveryExchange();
        processRequests(queueDelivery);
        closeResources();
    }

    private static void setupConnectionAndChannel() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.basicQos(10);
            setupRequestExchange();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setupRequestExchange() {
        try {
            channel.exchangeDeclare(READ_REQUEST_EXCHANGE_NAME, "fanout");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String setupDeliveryExchange() {
        try {
            channel.exchangeDeclare(DELIVERY_EXCHANGE_NAME, "fanout");
            String queueDelivery = channel.queueDeclare().getQueue();
            channel.queueBind(queueDelivery, DELIVERY_EXCHANGE_NAME, "");
            return queueDelivery;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void processRequests(String queueDelivery) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("To quit type 'quit'");
        System.out.println("To send a request type: 'Read Last' or 'Read All'");
        System.out.println("Enter a choice:");

        while (true) {
            String choice = scanner.nextLine();

            if ("quit".equalsIgnoreCase(choice)) {
                System.out.println("Quitting...");
                break;
            }

            switch (choice) {
                case "Read Last":
                    handleReadLastRequest(queueDelivery);
                    break;
                case "Read All":
                    handleReadAllRequest(queueDelivery);
                    break;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    private static void handleReadLastRequest(String queueDelivery) {
        try {
            final MutableBoolean messageProcessed = new MutableBoolean(false);
            publishMessage(READ_REQUEST_EXCHANGE_NAME, "Read Last");
            System.out.println(" [*] Waiting for message.");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                if (!messageProcessed.value) {
                    String messageReceived = new String(delivery.getBody(), "UTF-8");
                    System.out.println(" [x] Received '" + messageReceived + "' from Write Client");
                    System.out.print("Enter a choice: \n");
                    messageProcessed.value = true;
                }
            };
            channel.basicConsume(queueDelivery, true, deliverCallback, consumerTag -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleReadAllRequest(String queueDelivery) {
        Map<String, Integer> allMessages = new HashMap<>();
        CountDownLatch latch = new CountDownLatch(NUM_REPLICAS);

        try {
            publishMessage(READ_REQUEST_EXCHANGE_NAME, "Read All");
            System.out.println(" [*] Waiting for messages.");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String messageReceived = new String(delivery.getBody(), "UTF-8");
                if (messageReceived.equals("data all sent")) {
                    latch.countDown();
                } else {
                    String line = messageReceived.substring(2);
                    allMessages.put(line, allMessages.getOrDefault(line, 0) + 1);
                }
            };
            channel.basicConsume(queueDelivery, true, deliverCallback, consumerTag -> {});
            latch.await();

            System.out.println("Majority lines from all replicas:");
            allMessages.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .forEach(entry -> System.out.println(entry.getKey()));
            System.out.println("Enter a choice:");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void publishMessage(String exchangeName, String message) {
        try {
            channel.basicPublish(exchangeName, "", null, message.getBytes("UTF-8"));
            System.out.println(" [x] Sent '" + message + "' request");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void closeResources() {
        try {
            if (channel != null) channel.close();
            if (connection != null) connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class MutableBoolean {
        boolean value;

        MutableBoolean(boolean value) {
            this.value = value;
        }
    }
}
