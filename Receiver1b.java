import java.io.*;
import java.net.*;
import java.util.HashMap;

public class Receiver1b {
    public static void main(String args[]) throws Exception {
        System.out.println("Receiver 1b started");

        // Get the address, port and name of file to send over UDP
        final int port = Integer.parseInt(args[0]);
        final String fileName = args[1];

        receiveFile(port, fileName);
    }
    public static void sendAckPacket(int sequenceNumber, DatagramSocket receiverSocket, InetAddress hostAddress, int portNumber) throws IOException {
        // Resend acknowledgement
        byte[] ackPacketToSend = new byte[2];
        ackPacketToSend[0] = (byte)(sequenceNumber >> 8);
        ackPacketToSend[1] = (byte)(sequenceNumber);
        DatagramPacket acknowledgement = new  DatagramPacket(ackPacketToSend, ackPacketToSend.length, hostAddress, portNumber);
        receiverSocket.send(acknowledgement);
        System.out.println("Sent: ACK   Sequence Number = " + sequenceNumber);
    }
    public static void receiveFile(int port, String fileName) throws Exception {
        System.out.println("Waiting for file. . .");
        // create receiver socket
        DatagramSocket receiverSocket = new DatagramSocket(port);
        File file = new File(fileName);
        FileOutputStream fileStream = new FileOutputStream(file);

        //sequence numbers and flag
        int sequenceNumber = -1;
        int previousSequenceNumber = -1;
        boolean flagLastMessage = false;
        boolean lastMessage = false;
        HashMap<Integer,Boolean> sequencesReceived = new HashMap<Integer,Boolean> ();

        // for each incoming message
        while (!lastMessage) {
            // byte array for data (message without header)
            // byte array for full message (data + header)
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
                sequencesReceived.put((sequenceNumberA + sequenceNumberB), true);
                System.out.println("Received: Sequence number = " + sequenceNumber + ", Flag = " + flagLastMessage);

                // Send acknowledgement
                sendAckPacket(sequenceNumber, receiverSocket, hostAddress, portNumber);

              } else {
                    System.out.println("DISCARDED PACKET! EXPECTED:  Sequence number: " + (previousSequenceNumber + 1) + " but received " + sequenceNumber + "");
                    //Resend the acknowledgement
                    sendAckPacket(previousSequenceNumber, receiverSocket, hostAddress, portNumber);
                    flagLastMessage = false;
                    // throw new Exception("Corrupted packet"+sequenceNumber+" "+(sequenceNumberA + sequenceNumberB));
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
        System.out.println(fileName + " has been received and saved.");
        System.out.println(sequencesReceived.keySet().size());
    }
}
