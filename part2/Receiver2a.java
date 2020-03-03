import java.io.*;
import java.net.*;

public class Receiver2a {
    public static void main(String args[]) throws Exception {

        // Get the address, port and name of file to send over UDP
        final int port = Integer.parseInt(args[0]);
        final String fileName = args[1];

        receiveFile(port, fileName);
    }

    public static void sendAckPacket(int previousSequenceNumber, DatagramSocket receiverSocket, InetAddress hostAddress,
            int portNumber) throws IOException {
        // Resend acknowledgement
        byte[] ackPacketToSend = new byte[2];
        ackPacketToSend[0] = (byte) (previousSequenceNumber >> 8);
        ackPacketToSend[1] = (byte) (previousSequenceNumber);
        DatagramPacket acknowledgement = new DatagramPacket(ackPacketToSend, ackPacketToSend.length, hostAddress,
                portNumber);
        receiverSocket.send(acknowledgement);
        System.out.println("SENT: ACK: " + previousSequenceNumber);
    }

    public static void receiveFile(int port, String fileName) throws Exception {
        // create receiver socket
        DatagramSocket receiverSocket = new DatagramSocket(port);
        File file = new File(fileName);
        FileOutputStream fileStream = new FileOutputStream(file);

        // sequence numbers and flag
        int sequenceNumber = -1;
        // int previousSequenceNumber = 0;
        int expectedSequenceNum = 1;
        boolean flagLastMessage = false;
        boolean lastMessage = false;
        // for each incoming message
        while (!lastMessage) {
            byte[] buffer = new byte[1027];
            // Receive packet and retrieve message
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
            receiverSocket.setSoTimeout(0);
            receiverSocket.receive(receivedPacket);

            // retrieve portNumber and hostAddress for sending ack back
            int portNumber = receivedPacket.getPort();
            InetAddress hostAddress = receivedPacket.getAddress();

            byte[] messageReceived = new byte[receivedPacket.getLength()];
            byte[] dataReceived = new byte[receivedPacket.getLength() - 3];

            messageReceived = receivedPacket.getData();
            int sequenceNumberA = (messageReceived[0] & 0xff) << 8;
            int sequenceNumberB = (messageReceived[1] & 0xff);
            sequenceNumber = sequenceNumberA + sequenceNumberB;
            System.out.println("RECEIVED: " + sequenceNumber);
            if ((expectedSequenceNum) == sequenceNumber) {
                // check header to see if it's the last message
                if ((messageReceived[2] & 0xff) == 1) {
                    flagLastMessage = true;
                } else {
                    flagLastMessage = false;
                }
                // get data from message received

                for (int i = 0; i < dataReceived.length; i++) {
                    dataReceived[i] = messageReceived[i + 3];
                }

                // save data into a new file with name as fileName
                fileStream.write(dataReceived);
                // Send acknowledgement
                sendAckPacket(expectedSequenceNum, receiverSocket, hostAddress, portNumber);
                expectedSequenceNum += 1;

            } else {
                // Resend the acknowledgement
                sendAckPacket(expectedSequenceNum, receiverSocket, hostAddress, portNumber);
                flagLastMessage = false;
            }
            // if it was the last message to be received close file stream
            if (flagLastMessage) {
                fileStream.close();
                receiverSocket.close();
                lastMessage = true;
            }
        }

        // close socket once done
        receiverSocket.close();
        // confirmation message
        // System.out.println("\n=============================== C O M P L E T E D
        // ===============================");
        // System.out.println("\n Received: "+fileName);
    }
}
