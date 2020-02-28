
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Semaphore;

class SendPacket implements Runnable {
    DatagramSocket senderSocket;
    InetAddress ipAddress;
    int portNumber;
    byte[] header;
    byte[] data;
    int packetSize;
    Integer startTimer;

    public SendPacket(DatagramSocket senderSocket, InetAddress ipAddress, int portNumber, byte[] header, byte[] data,
            int packetSize, Integer startTimer) {
        this.senderSocket = senderSocket;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.header = header;
        this.data = data;
        this.packetSize = packetSize;
        this.startTimer = startTimer;
    }

    @Override
    public void run() {
        byte[] messageToSend = new byte[this.packetSize];
        messageToSend[0] = header[0];
        messageToSend[1] = header[1];
        messageToSend[2] = header[3];
        for (int i = 3; i < packetSize; i++) {
            messageToSend[i] = data[i - 3];
        }
        DatagramPacket packetToSend = new DatagramPacket(messageToSend, messageToSend.length, this.ipAddress,
                this.portNumber);
        this.senderSocket.send(packetToSend);
        if (this.startTimer != null) {
            senderSocket.setSoTimeout(this.startTimer);
        }

    }
}

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

        int sequenceNumber = 0;
        boolean flagLastMessage = false;
        // sequence number to keep track the acknowledged packets
        int sequenceNumberACK = 0;

        int retransmissionCounter = 0;
        int retransmissionTimeout = retryTimeout;

        int base = 1;
        int nextSeqNum = 1;

        // time needed to calculate avg throughput at the end
        Date date = new Date();
        long timeStartedSendingMS = date.getTime();

        // for each message that is being generated
        for (int i = 0; i < fileByteArray.length; i += 1024 * windowSize) {
            if (nextSeqNum < base + windowSize) {
                for (int j = i; j <= i * 1024 * windowSize; j += 1024) {
                    if (j >= fileByteArray.length) {
                        break;
                    }
                    byte[] header = { (byte) (sequenceNumber >> 8), (byte) (sequenceNumber), (byte) 0 };
                    byte[] data;
                    int packetSize = 1024;
                    if ((i + 1024) >= fileByteArray.length) {
                        // set flagLastMessage to 1 if it's the last packet to send
                        flagLastMessage = true;
                        header[2] = (byte) (1);
                        data = Arrays.copyOfRange(fileByteArray, j, fileByteArray.length - 1);
                        packetSize = (fileByteArray.length - 1) + 3;

                    } else {
                        flagLastMessage = false;
                        header[2] = (byte) (0);
                        data = Arrays.copyOfRange(fileByteArray, j, j + 1024);
                    }
                    // start thread to send packet
                    SendPacket sendPacket;
                    if (base == nextSeqNum) {
                        sendPacket = new SendPacket(senderSocket, ipAddress, portNumber, header, data, packetSize,
                                retryTimeout);
                    } else {
                        sendPacket = new SendPacket(senderSocket, ipAddress, portNumber, header, data, packetSize,
                                null);
                    }
                    Thread thread = new Thread(sendPacket);
                    thread.start();
                    nextSeqNum += 1;
                }
            }
        }
    }
}
