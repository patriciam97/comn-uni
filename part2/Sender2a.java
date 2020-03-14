import java.io.*;
import java.net.*;
import java.util.*;

public class Sender2a {

    public static class ReceiveThread implements Runnable {
        @Override
        public void run() {
            while (!lastPacketAck) {
                byte[] ack = new byte[2];
                DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
                try {
                    senderSocket.receive(ackPacket);
                } catch (IOException e) {
                    System.out.println(e);
                }
                Integer sequenceNumberACK = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                System.out.println("Received: " + sequenceNumberACK + " " + finalPacketId);
                if (sequenceNumberACK == finalPacketId - 1) {
                    System.out.println("here");
                    synchronized (lastPacketAck) {
                        lastPacketAck = true;
                        // resendTask.cancel();
                        timer.cancel();
                    }
                } else {
                    if (sequenceNumberACK < base)
                        continue;
                    int i = 0;
                    if (windowPackets.size() == 0)
                        continue;
                    synchronized (Sender2a.class) {
                        System.out.println(sequenceNumberACK + "-" + base + "-" + windowPackets.size());
                        Integer diff = Math.abs(sequenceNumberACK - base) +1;
                        // for (int i = 0; i < diff; i++) {
                        // System.out.println("REMOVED 1");
                        // windowPackets.remove(i);
                        // }
                        do {
                            System.out.println("REMOVED 1");
                            windowPackets.removeFirst();
                            i++;
                        } while (i < diff);
                        System.out.println("BASE MOVED FROM " + base + " TO " + (sequenceNumberACK + 1));
                        base = sequenceNumberACK + 1;
                        if (base == nextSeqNum) {
                            resendTask.cancel();

                        } else {
                            resendTask = new ResendWindowTask();
                            timer.schedule(resendTask, retryTimeout);
                        }
                    }
                }

            }
        }
    }

    static class ResendWindowTask extends TimerTask {

        @Override
        public void run() {
            // send window packets, timer will start automatically
            while (!windowPackets.isEmpty() && !lastPacketAck) {
                System.out.println("RE-SEND HAPPENED: " + windowPackets.size());
                for (int i = 0; i < windowPackets.size(); i++) {
                    try {
                        senderSocket.send(windowPackets.get(i));
                    } catch (IOException e) {
                        System.out.println(e);
                    }
                }
            }
        }
    }

    static int base = 0;
    static int nextSeqNum = 0;
    static Integer retryTimeout = 0;
    static int finalPacketId = 0;
    static DatagramSocket senderSocket;
    static Timer timer = new Timer();
    static LinkedList<DatagramPacket> windowPackets = new LinkedList<DatagramPacket>();
    static Boolean lastPacketAck = false;
    static ResendWindowTask resendTask = new ResendWindowTask();

    public static void main(String args[]) throws Exception {

        final String hostName = args[0];
        final int portNumber = Integer.parseInt(args[1]);
        final String fileName = args[2];
        retryTimeout = Integer.parseInt(args[3]);
        final int windowSize = Integer.parseInt(args[4]);
        sendFile(hostName, portNumber, fileName, windowSize);
    }

    public static void sendFile(String hostName, int portNumber, String fileName, int windowSize)
            throws IOException, Exception {

        senderSocket = new DatagramSocket();
        InetAddress ipAddress = InetAddress.getByName(hostName);
        File file = new File(fileName);
        InputStream fileStream = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int) file.length()];
        fileStream.read(fileByteArray);
        fileStream.close();
        finalPacketId = (int) Math.ceil((double) file.length() / 1024);
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
        System.out.println(timer);
        // timer.schedule(new resendWindowTask(), 0, retryTimeout);

        while (!lastPacketAck) {
            // // System.out.println("Base: " + base + ", NextSeqNum: " + nextSeqNum + "
            // // Window: " + windowSize);
            // synchronized (Sender2a.class) {
            if ((nextSeqNum < base + windowSize) && nextSeqNum < finalPacketId) {
                System.out.println("What");
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
                synchronized (Sender2a.class) {
                    windowPackets.add(packetToSend);
                    System.out.println("Sent: Sequence number = " + nextSeqNum + " Flag = " + flagLastMessage
                            + " Length: " + messageToSend.length);
                    if (base == nextSeqNum) {
                        System.out.println("WHAT");
                        resendTask = new ResendWindowTask();
                        timer.schedule(resendTask, retryTimeout);
                    }
                    nextSeqNum += 1;
                }

            }
            // }

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
