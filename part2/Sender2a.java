import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Sender2a {

    public static class ReceiveThread implements Runnable {
        @Override
        public void run() {
            while (true) {
                byte[] ack = new byte[2];
                DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
                try {
                    senderSocket.receive(ackPacket);
                } catch (IOException e) {

                }
                Integer sequenceNumberACK = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                if (sequenceNumberACK == finalPacketId - 1) {
                    synchronized (lastPacketAck) {
                        lastPacketAck = true;
                    }
                } else {
                    synchronized (Sender2a.class) {
                        Integer diff = Math.abs(sequenceNumberACK - base);
                        for (int i = 0; i < diff; i++) {
                            windowPackets.remove();
                        }
                        base = sequenceNumberACK;
                    }
                }

            }
        }
    }

    static class resendWindowTask extends TimerTask {

        @Override
        public void run() {
            // send window packets, timer will start automatically
            synchronized (Sender2a.class) {
                while (!windowPackets.isEmpty()) {
                    DatagramPacket packet = windowPackets.remove();
                    senderSocket.send(packet);
                    windowPackets.add(packet);
                }
            }
        }
    }

    static int base = 0;
    static int nextSeqNum = 0;
    static int finalPacketId = 0;
    static DatagramSocket senderSocket;
    static Timer timer;
    static Queue<DatagramPacket> windowPackets = new LinkedList<DatagramPacket>();
    static Boolean lastPacketAck = false;

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

        senderSocket = new DatagramSocket();
        InetAddress ipAddress = InetAddress.getByName(hostName);
        File file = new File(fileName);
        InputStream fileStream = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int) file.length()];
        fileStream.read(fileByteArray);
        fileStream.close();
        int finalPacketId = (int) Math.ceil((double) file.length() / 1024);
        boolean flagLastMessage = false;
        // // sequence number to keep track the acknowledged packets
        // time needed to calculate avg throughput at the end
        Date date = new Date();
        long timeStartedSendingMS = date.getTime();
        // thread to receive packets
        Runnable runnable = new ReceiveThread();
        Thread thread = new Thread(runnable);
        thread.start();
        // timer needed to resend packets
        Timer timer = new Timer();
        // timer.schedule(new resendWindowTask(), 0, retryTimeout);

        while (!lastPacketAck) {
            // // System.out.println("Base: " + base + ", NextSeqNum: " + nextSeqNum + "
            // // Window: " + windowSize);
            synchronized (Sender2a.class) {
                if ((nextSeqNum < base + windowSize) && nextSeqNum < finalPacketId) {
                    byte[] messageToSend = new byte[1027];
                    if ((nextSeqNum * 1024) + 1024 >= fileByteArray.length) {
                        messageToSend = new byte[fileByteArray.length - (nextSeqNum * 1024) + 3];
                        flagLastMessage = true;
                    } else {
                        flagLastMessage = false;
                    }
                    messageToSend[0] = (byte) (nextSeqNum >> 8);
                    messageToSend[1] = (byte) (nextSeqNum);

                    if (flagLastMessage) {
                        messageToSend[2] = (byte) 1;
                        for (int j = 0; j < (fileByteArray.length - (nextSeqNum * 1024)); j++) {
                            messageToSend[j + 3] = fileByteArray[(nextSeqNum * 1024) + j];
                        }
                    } else {
                        messageToSend[2] = (byte) 0;
                        for (int j = 0; j <= 1023; j++) {
                            messageToSend[j + 3] = fileByteArray[(nextSeqNum * 1024) + j];
                        }
                    }
                    DatagramPacket packetToSend = new DatagramPacket(messageToSend, messageToSend.length, ipAddress,
                            portNumber);
                    senderSocket.send(packetToSend);
                    windowPackets.add(packetToSend);
                    System.out.println("Sent: Sequence number = " + nextSeqNum + " Flag = " + flagLastMessage
                            + " Length: " + messageToSend.length);
                    if (base == nextSeqNum) {
                        timer.schedule(new resendWindowTask(), 0, retryTimeout);
                    }
                    nextSeqNum += 1;
                }
            }

        }
        // executor.shutdown();
        senderSocket.close();
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