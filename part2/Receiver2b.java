import java.io.*;
import java.net.*;
import java.util.HashMap;

public class Receiver2b {
    public static void main(String args[]) throws Exception {

        // Get the address, port and name of file to send over UDP
        final int port = Integer.parseInt(args[0]);
        final String fileName = args[1];
        final int windowSize = Integer.parseInt(args[2]);
        receiveFile(port, fileName, windowSize);
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
        // System.out.println("SENT: ACK: " + previousSequenceNumber);
    }

    public static void receiveFile(int port, String fileName, int windowSize) throws Exception {
        // create receiver socket
        DatagramSocket receiverSocket = new DatagramSocket(port);
        File file = new File(fileName);
        FileOutputStream fileStream = new FileOutputStream(file);
        // sequence numbers for calculations
        int sequenceNumber;
        boolean flagLastMessage = false;
        boolean lastMessage = false;
        int base = 0;
        HashMap<Integer, byte[]> windowBuffer = new HashMap<Integer, byte[]>();
        // for each incoming message
        while (!lastMessage) {
            byte[] buffer = new byte[1027];
            // Receive packet and retrieve message
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
            // receiverSocket.setSoTimeout(0);
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
            if ((sequenceNumber >= base) && (sequenceNumber <= base + windowSize - 1)) {
                // case 1: packets falls within window. Selective ACK is returned
                windowBuffer.put(sequenceNumber, messageReceived); // messagereceived still has header
                sendAckPacket(sequenceNumber, receiverSocket, hostAddress, portNumber);
                if (sequenceNumber == base) {
                    int max = base;
                    while (true) {
                        if (windowBuffer.containsKey(max + 1)) {
                            max += 1;
                        } else {
                            break;
                        }
                    }
                    // write all packets from base to max
                    for (int i = base; i <= max; i++) {
                        byte[] packet = windowBuffer.get(i);
                        if ((packet[2] & 0xff) == 1) {
                            flagLastMessage = true;
                            // System.out.println("FLAG " + flagLastMessage);
                        } else {
                            flagLastMessage = false;
                        }
                        // get data from message received
                        for (int j = 0; j < dataReceived.length; j++) {
                            dataReceived[j] = messageReceived[j + 3];
                        }
                        // save data into a new file with name as fileName
                        fileStream.write(dataReceived);

                        if (flagLastMessage) {
                            for (int j = 0; j < 10; j++) {
                                sendAckPacket(sequenceNumber, receiverSocket, hostAddress, portNumber);
                            }
                            fileStream.close();
                            receiverSocket.close();
                            lastMessage = true;
                        }
                    }
                    base = max + 1;
                }
            } else {
                windowBuffer.put(sequenceNumber, messageReceived); // messagereceived still has header
                sendAckPacket(sequenceNumber, receiverSocket, hostAddress, portNumber);
            }

        }

        // close socket once done
        receiverSocket.close();
        // confirmation message
        // System.out.println("\n=============================== C O M P L E T E D
        // ===============================");
        System.out.println("Received: " + fileName);
    }
}
