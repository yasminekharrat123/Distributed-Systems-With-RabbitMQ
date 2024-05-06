package yas;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.Scanner;

public class ClientWriter {
    private final static String EXCHANGE_NAME = "WriterEXCHANGE" ;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        try (
                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel()
        ) {
            channel.exchangeDeclare(EXCHANGE_NAME, "fanout");


            System.out.print("Write 'quit' to shutdown \n" );

            while (true) {
                System.out.print("Enter a message: ");
                String message = scanner.nextLine();
                if (message.equals("quit")) {
                    break;
                }
                channel.basicPublish(EXCHANGE_NAME , "", null, message.getBytes("UTF-8"));
                System.out.println(" [x] Sent :'" + message + "'");
            }
        }
        catch (Exception e) {
            e.printStackTrace();

        }
    }
}
