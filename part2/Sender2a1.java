
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class AckTimeout implements Callable<Boolean> {
    @Override
    public Boolean call() throws Exception {
        System.out.println("Tmeout thread started");
        Thread.sleep(2000);
        return new Boolean(true);
    }
}

public class Sender2a1 {

    public static void main(String args[]) throws Exception {

        final String hostName = args[0];
        final int portNumber = Integer.parseInt(args[1]);
        final String fileName = args[2];
        final int retryTimeout = Integer.parseInt(args[3]);
        final int windowSize = Integer.parseInt(args[4]);
        sendFile(hostName, portNumber, fileName, retryTimeout, windowSize);
    }

    public static void sendFile(String hostName, int portNumber, String fileName, int retryTimeout, int windowSize)
            throws IOException, Exception {
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
        int maxRetransmissionsLastPackage = 10;

        int base = 1;
        int nextSeqNum = 1;
        int finalPacketId = (int) Math.ceil((double) file.length() / 1024);
        boolean sendNextBatch = true;
        boolean timeoutDone = false;
        boolean ackRecievedSomething = false;
        boolean ackPacketReceived = false;
        // time needed to calculate avg throughput at the end
        Date date = new Date();
        long timeStartedSendingMS = date.getTime();
        boolean ackLastFileReceived = false;
        ExecutorService executor = null;
        Future<Boolean> futureCall = null;

        while (!ackLastFileReceived) {
            System.out.println("Base: " + base + " Next seq num: " + nextSeqNum + "window: " + windowSize);
            if (nextSeqNum < base + windowSize) {
                byte[] messageToSend = new byte[1027];
                if ((nextSeqNum + 1024) >= fileByteArray.length) {
                    messageToSend = new byte[fileByteArray.length - nextSeqNum];
                }
                for (int i = base; i < base + windowSize; i++) {
                    sequenceNumber += 1;
                    messageToSend[0] = (byte) (sequenceNumber >> 8);
                    messageToSend[1] = (byte) (sequenceNumber);
                    flagLastMessage = (nextSeqNum + 1024) >= fileByteArray.length;
                    if (flagLastMessage) {
                        messageToSend[2] = (byte) 1;
                        for (int j = 0; j < (fileByteArray.length - i); j++) {
                            messageToSend[j + 3] = fileByteArray[i + j];
                        }
                    } else {
                        messageToSend[2] = (byte) 0;
                        for (int j = 0; j <= 1023; j++) {
                            messageToSend[j + 3] = fileByteArray[i + j];
                        }
                    }
                    DatagramPacket packetToSend = new DatagramPacket(messageToSend, messageToSend.length, ipAddress,
                            portNumber);
                    senderSocket.send(packetToSend);
                    System.out.println("Sent: Sequence number = " + sequenceNumber + "   Flag = " + flagLastMessage
                            + "   Length: " + messageToSend.length);
                    if (base == nextSeqNum) {
                        System.out.println("send.");
                        timeoutDone = false;
                        executor = Executors.newCachedThreadPool();
                        futureCall = executor.submit(new AckTimeout());
                        timeoutDone = futureCall.get();
                        futureCall.cancel(true);
                        executor.shutdown();
                    }
                    nextSeqNum += 1;
                }
            }
            while (!ackRecievedSomething && !timeoutDone) {
                System.out.println(("here"));
                byte[] ack = new byte[2];
                DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
                senderSocket.setSoTimeout(25);
                try {
                    senderSocket.receive(ackPacket);
                    sequenceNumberACK = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    System.out.println("Received: Sequence number = " + sequenceNumberACK);
                    ackPacketReceived = true;

                } catch (SocketTimeoutException e) {
                }

                if ((sequenceNumberACK == sequenceNumber) && (ackPacketReceived)) {
                    ackRecievedSomething = true;
                    base = sequenceNumberACK + 1;
                    if (base == nextSeqNum) {
                        try {
                            futureCall.cancel(true);
                            executor.shutdown();
                        } catch (Exception e) {
                            System.out.println(("caught " + e));
                        }

                    } else {
                        System.out.println("Restarting ACKTimeout Thread");
                        executor = Executors.newCachedThreadPool();
                        futureCall = executor.submit(new AckTimeout());
                        timeoutDone = futureCall.get(); // Here the thread will be blocked
                        break;
                    }
                }
            }
            if (timeoutDone) {
                // done to re-send everything
                nextSeqNum = base;
                sequenceNumber = base - 1;
            }
        }
        // at then end
        senderSocket.close();
        fileStream.close();

    }
}