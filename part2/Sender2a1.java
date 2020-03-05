import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class AckTimeout implements Callable<Boolean> {
    Integer retryTimeout;
    @Override
    public Boolean call() throws Exception {
        // System.out.println("Timeout thread started");
        Thread.sleep(this.retryTimeout);
        return new Boolean(true);
    }
}
class AckReceive implements Callable<Integer> {
    DatagramSocket senderSocket;
    @Override
    public Integer call() throws Exception {
        // System.out.println(("Waiting for ack, timeout not done yet"));
        byte[] ack = new byte[2];
        DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
        this.senderSocket.receive(ackPacket);
        Integer sequenceNumberACK = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
        return sequenceNumberACK;
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

        // int sequenceNumber = 0;
        boolean flagLastMessage = false;
        // // sequence number to keep track the acknowledged packets
        Integer sequenceNumberACK = null;
        Integer previousSequenceNumberACK = 0;
        int base = 1;
        int nextSeqNum = 1;
        int finalPacketId = (int) Math.ceil((double) file.length() / 1024);
        boolean timeoutDone = false;
        // time needed to calculate avg throughput at the end
        Date date = new Date();
        long timeStartedSendingMS = date.getTime();
        ExecutorService executor = null;
        Future<Boolean> futureCall = null;
        Future<Integer> futureAck = null;
        // Thread waiting to receive things is always running in the background
        executor = Executors.newCachedThreadPool();
        AckReceive ackReceive = new AckReceive();
        ackReceive.senderSocket = senderSocket;
        futureAck = executor.submit(ackReceive);
        AckTimeout ackThread = new AckTimeout();
        ackThread.retryTimeout = retryTimeout;
        
        // while loop is responsible to send packets
        while (true) {
            // System.out.println("Base: " + base + ", NextSeqNum: " + nextSeqNum + ", Window: " + windowSize);
            if (nextSeqNum < base + windowSize) {
                byte[] messageToSend = new byte[1027];
                if ((nextSeqNum + 1024) >= fileByteArray.length) {
                    messageToSend = new byte[fileByteArray.length - nextSeqNum];
                }
                for (int i = base; i < base + windowSize; i++) {
                    messageToSend[0] = (byte) (nextSeqNum >> 8);
                    messageToSend[1] = (byte) (nextSeqNum);
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
                    System.out.println("Sent: Sequence number = " + nextSeqNum + "   Flag = " + flagLastMessage
                            + "   Length: " + messageToSend.length);
                    if (base == nextSeqNum) {
                        timeoutDone = false;
                        futureCall = executor.submit(ackThread);
                        // timeoutDone = futureCall.get();
                        // System.out.println("Timeout");
                        // futureCall.cancel(true);
                        // System.out.println("Cancelled");
                    }
                    nextSeqNum += 1;
                }
            }
            // System.out.println("here1");
            timeoutDone = futureCall.get();
            // System.out.println("here2");
            sequenceNumberACK = futureAck.get();
            futureAck = executor.submit(ackReceive);
            // System.out.println("here3");
            if (sequenceNumberACK>previousSequenceNumberACK) {
                if (finalPacketId == sequenceNumberACK){
                    break;
                }
                base = sequenceNumberACK+ 1;
                // System.out.println(("BASE MOVED TO : "+ base));
                if (base == nextSeqNum) {
                    // System.out.println("here4");
                    try {
                        timeoutDone = false;
                        futureCall = executor.submit(ackThread);
                    } catch (Exception e) {
                        System.out.println(("caught " + e));
                    }
                    // System.out.println("here5");

                } else {
                    // System.out.println("Restarting ACKTimeout Thread");
                    futureCall = executor.submit(ackThread);
                    timeoutDone = futureCall.get(); // Here the thread will be blocked
                }
            }
            if (timeoutDone) {
                // done to re-send everything
                System.out.println("Resending from " + base + " to " + (base + windowSize - 1));
                nextSeqNum = base;
                // System.out.println(("NEXT SEQ NUM MOVED TO : "+ base));

                // sequenceNumber = base - 1;
            }
        }
        // at then end
        senderSocket.close();
        fileStream.close();
        // Calculate the average throughput
        int filesizeKB = (fileByteArray.length) / 1027;
        date = new Date();
        long timeDoneSendingMS = date.getTime();
        double transferTime = (timeDoneSendingMS - timeStartedSendingMS)/ 1000;
        double throughput = (double) filesizeKB / transferTime;
        System.out.println(throughput);
    }
}
