
import java.io.*;
import java.net.*;
import java.util.Date;

public class Sender2a {

    public static void main(String args[]) throws Exception {

        final String hostName = args[0];
        final int portNumber = Integer.parseInt(args[1]);
        final String fileName = args[2];
        final int retryTimeout = Integer.parseInt(args[3]);
        final int windowSize = Integer.parseInt(args[4]);
        sendFile(hostName, portNumber, fileName, retryTimeout, windowSize);
    }

    public static void sendFile(String hostName, int portNumber, String fileName, int retryTimeout, int windowSize)
            throws IOException {
        DatagramSocket senderSocket = new DatagramSocket();
        InetAddress ipAddress = InetAddress.getByName(hostName);
        File file = new File(fileName);
        InputStream fileStream = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int) file.length()];
        fileStream.read(fileByteArray);
        fileStream.close();
        // time needed to calculate avg throughput at the end
        Date date = new Date();
        long timeStartedSendingMS = date.getTime();

        int sequenceNumber = 0;
        boolean flagLastMessage = false;
        // sequence number to keep track the acknowledged packets
        int sequenceNumberACK = 0;

        int retransmissionCounter = 0;
        int retransmissionTimeout = retryTimeout;
        // for each message that is being generated
        for (int i = 0; i < fileByteArray.length; i += 1024) {
            sequenceNumber += 1;
            byte[] messageToSend = new byte[1027];
            messageToSend[0] = (byte) (sequenceNumber >> 8);
            messageToSend[1] = (byte) (sequenceNumber);

            // check if this packet is the last packet
            if ((i + 1024) >= fileByteArray.length) {
                // set flagLastMessage to 1 if it's the last packet to send
                flagLastMessage = true;
                // add it in the header
                messageToSend[2] = (byte) (1);
            } else {
                flagLastMessage = false;
                messageToSend[2] = (byte) (0);
            }
            // append data bytes
            if (!flagLastMessage) {
                for (int j = 0; j <= 1023; j++) {
                    messageToSend[j + 3] = fileByteArray[i + j];
                }
            } else if (flagLastMessage) {
                // append whatever is left
                messageToSend = new byte[(fileByteArray.length - i) + 3];
                for (int j = 0; j < (fileByteArray.length - i); j++) {
                    messageToSend[j + 3] = fileByteArray[i + j];
                }
                messageToSend[0] = (byte) (sequenceNumber >> 8);
                messageToSend[1] = (byte) (sequenceNumber);
                messageToSend[2] = (byte) (1);
            }
            DatagramPacket packetToSend = new DatagramPacket(messageToSend, messageToSend.length, ipAddress,
                    portNumber);
            senderSocket.send(packetToSend);
            int maxRetransmissionsLastPackage = 9;
            // verifying acknowledgements
            boolean ackRecievedSomething = false;
            boolean ackPacketReceived = false;

            while (!ackRecievedSomething) {

                byte[] ack = new byte[2];
                DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);

                try {

                    senderSocket.setSoTimeout(retransmissionTimeout);
                    senderSocket.receive(ackPacket);
                    sequenceNumberACK = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;

                } catch (SocketTimeoutException e) {

                    ackPacketReceived = false;
                }

                // Break if there is an ack so that the next packet can be sent
                if ((sequenceNumberACK == sequenceNumber) && (ackPacketReceived)) {
                    ackRecievedSomething = true;
                    break;
                } else if (maxRetransmissionsLastPackage >= 0 && flagLastMessage) { // Resend packet
                    senderSocket.send(packetToSend);
                    maxRetransmissionsLastPackage -= 1;
                    retransmissionCounter += 1;
                } else if (maxRetransmissionsLastPackage == -1 && flagLastMessage) {
                    break;
                } else {
                    senderSocket.send(packetToSend);
                    retransmissionCounter += 1;
                }
            }
        }
        senderSocket.close();
        // Calculate the average throughput
        int filesizeKB = (fileByteArray.length) / 1027;
        date = new Date();
        long timeDoneSendingMS = date.getTime();
        double transferTime = (timeDoneSendingMS - timeStartedSendingMS) / 1000;
        double throughput = (double) filesizeKB / transferTime;
        System.out.println(retransmissionCounter + " " + throughput);
    }
}
