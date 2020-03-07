import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Sender2a2 {

    public static void main(String args[]) throws Exception {

        final String hostName = args[0];
        final int portNumber = Integer.parseInt(args[1]);
        final String fileName = args[2];
        final int retryTimeout = Integer.parseInt(args[3]);
        final int windowSize = Integer.parseInt(args[4]);
        sendFile(hostName, portNumber, fileName, retryTimeout, windowSize);
    }

    public static void sendFile(String hostName, int portNumber, String fileName, final int retryTimeout,
            int windowSize) throws IOException, Exception {
        // socket is used to receive acks and set timeout error
        // DatagramSocket senderSocket = new DatagramSocket();
        // InetAddress ipAddress = InetAddress.getByName(hostName);
        // channel is used to send packets
        DatagramChannel channel = DatagramChannel.open();
        channel.socket().bind(new InetSocketAddress(hostName, portNumber));
        File file = new File(fileName);
        InputStream fileStream = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int) file.length()];
        fileStream.read(fileByteArray);
        fileStream.close();

        // int sequenceNumber = 0;
        boolean flagLastMessage = false;
        // // sequence number to keep track the acknowledged packets
        Integer sequenceNumberACK = null;
        Integer previousSeqNumAck = -1;
        int base = 0;
        int nextSeqNum = 0;
        int finalPacketId = (int) Math.ceil((double) file.length() / 1024);
        boolean lastPacketAck = false;
        // time needed to calculate avg throughput at the end
        Date date = new Date();
        long timeStartedSendingMS = date.getTime();
        Runnable timeoutRunnable = new Runnable() {
            // final retryTimeout = retryTimeout;
            public void run() {
                try {
                    Thread.sleep(retryTimeout);
                } catch (Exception e) {

                }
            }
        };
        Thread timeoutThread = new Thread(timeoutRunnable);
        while (!lastPacketAck) {
            // System.out.println("here2");
            // System.out.println("Base: " + base + ", NextSeqNum: " + nextSeqNum + ",
            // Window: " + windowSize);
            if (nextSeqNum < base + windowSize) {
                byte[] messageToSend = new byte[1027];
                int max = base + windowSize;
                if (((base + windowSize) * 1024) > fileByteArray.length) {
                    max = finalPacketId;
                }
                for (int i = base; i < max; i++) {

                    if ((i * 1024) + 1024 >= fileByteArray.length) {
                        messageToSend = new byte[fileByteArray.length - (i * 1024) + 3];
                        flagLastMessage = true;
                    } else {
                        flagLastMessage = false;
                    }
                    messageToSend[0] = (byte) (nextSeqNum >> 8);
                    messageToSend[1] = (byte) (nextSeqNum);
                    // flagLastMessage = ((nextSeqNum*1024) + 1024) >= fileByteArray.length;

                    if (flagLastMessage) {
                        messageToSend[2] = (byte) 1;
                        for (int j = 0; j < (fileByteArray.length - (i * 1024)); j++) {
                            messageToSend[j + 3] = fileByteArray[i + j];
                        }
                    } else {
                        messageToSend[2] = (byte) 0;
                        for (int j = 0; j <= 1023; j++) {
                            messageToSend[j + 3] = fileByteArray[i + j];
                        }
                    }
                    // DatagramPacket packetToSend = new DatagramPacket(messageToSend,
                    // messageToSend.length, ipAddress,
                    // portNumber);
                    // senderSocket.send(packetToSend);

                    ByteBuffer buf = ByteBuffer.allocate(messageToSend.length);
                    buf.clear();
                    buf.put(messageToSend);
                    buf.flip();
                    channel.send(buf, new InetSocketAddress(hostName, portNumber));

                    // System.out.println("Sent: Sequence number = " + nextSeqNum + " Flag = " +
                    // flagLastMessage
                    // + " Length: " + messageToSend.length);
                    if (base == nextSeqNum) {
                        try {
                            timeoutThread.start();
                        } catch (Exception e) {
                            timeoutThread.run();
                        }
                    }
                    nextSeqNum += 1;
                }
            }
            ByteBuffer buf = ByteBuffer.allocate(2);
            buf.clear();
            channel.receive(buf);

            if ((((buf.get(0) & 0xff) << 8) + (buf.get(1) & 0xff)) < previousSeqNumAck) {
                sequenceNumberACK = ((buf.get(0) & 0xff) << 8) + (buf.get(1) & 0xff);
                previousSeqNumAck = sequenceNumberACK;
                System.out.println(sequenceNumberACK + " " + finalPacketId);
                if (finalPacketId == sequenceNumberACK) {
                    break;
                }
                base = sequenceNumberACK + 1;
                if (base == nextSeqNum) {
                    try {

                        timeoutThread.interrupt();
                    } catch (Exception e) {
                        System.out.println(("caught " + e));
                    }
                } else {
                    timeoutThread.interrupt();
                    timeoutThread.run();
                }
                if (sequenceNumberACK == finalPacketId - 1) {
                    System.out.println("here3");
                    lastPacketAck = true;
                    break;
                }
            }
            if (!timeoutThread.isAlive()) {
                // done to re-send everything
                int max = base + windowSize;
                if (((base + windowSize) * 1024) > fileByteArray.length) {
                    max = finalPacketId;
                }
                // System.out.println("Resending from " + base + " to " + max);
                nextSeqNum = base;
                // timeoutThread.interrupt();
            }
        }
        // System.out.println("here");
        timeoutThread.interrupt();
        // futureAck.cancel(true);
        // executor.shutdown();
        // at then end
        // senderSocket.close();
        channel.close();
        fileStream.close();
        // Calculate the average throughput
        int filesizeKB = (fileByteArray.length) / 1027;
        date = new Date();
        long timeDoneSendingMS = date.getTime();
        double transferTime = (timeDoneSendingMS - timeStartedSendingMS) / 1000;
        double throughput = (double) filesizeKB / transferTime;
        System.out.println(throughput);
    }
}