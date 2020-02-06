import java.io.*;
import java.net.*;

public class Receiver1a {

    public static void main(String args[]) throws Exception {
        System.out.println("Receiver 1a started");

        // Get the address, port and name of file to send over UDP
        final int port = Integer.parseInt(args[0]);
        final String fileName = args[1];

        receiveFile(port, fileName);
    }

    public static void receiveFile(int port, String fileName) throws Exception {
        System.out.println("Waiting . . .");
        // create receiver socket
        DatagramSocket receiverSocket = new DatagramSocket(port);
        InetAddress address;
        File file = new File(fileName);
        FileOutputStream fileStream = new FileOutputStream(file);

        //sequence number and flag
        int sequenceNumber = 0;
        boolean flagLastMessage = false;
        boolean lastMessage = false;

        // for each incoming message
        while (!lastMessage) {
            // byte array for data (message without header)
            // byte array for full message (data + header)
            byte[] buffer = new byte[1027];
            // Receive packet and retrieve message
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(receivedPacket);

            byte[] messageReceived = new byte[receivedPacket.getLength()];
            byte[] dataReceived = new byte[receivedPacket.getLength() - 3];

            messageReceived = receivedPacket.getData();


            // duplicate detection at the receiver
            int sequenceNumberA = (messageReceived[0] & 0xff) << 8;
            int sequenceNumberB = (messageReceived[1] & 0xff);

            if ((sequenceNumber+1) ==  (sequenceNumberA + sequenceNumberB)) {

                sequenceNumber = (sequenceNumberA + sequenceNumberB);

                // check header to see if it's the last message
                if ((messageReceived[2] & 0xff) == 1) {
                    flagLastMessage = true;
                } else {
                    flagLastMessage = false;
                }
                // get data from message received

                for (int i=0; i < dataReceived.length; i++) {
                    dataReceived[i] = messageReceived[i+3];
                }

                // save data into a new file with name as fileName
                fileStream.write(dataReceived);
                System.out.println("Received: Sequence number = " + sequenceNumber + ", Flag = " + flagLastMessage);

                // if it was the last message to be received close file stream
                if (flagLastMessage) {
                    fileStream.close();
                    receiverSocket.close();
                    lastMessage = true;
                    // break;
                }
            } else {
                throw new Exception("Corrupted packet");
            }
            if (flagLastMessage) {
              break;
            }
        }

        // close socket once done
        receiverSocket.close();
        // confirmation message
        System.out.println(fileName + " has been received and saved.");
    }
}
