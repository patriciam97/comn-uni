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
            while (!lastPacketAck) {
                byte[] ack = new byte[2];
                DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
                try {
                    senderSocket.receive(ackPacket);
                } catch (IOException e) {
                  System.out.println(e);
                }
                Integer sequenceNumberACK = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                System.out.println("Received: "+ sequenceNumberACK+" "+ finalPacketId);
                if (sequenceNumberACK == finalPacketId-1) {
                  System.out.println("here");
                    synchronized (lastPacketAck) {
                        lastPacketAck = true;
                        timer.cancel();
                    }
                } else {
                    synchronized (Sender2a.class) {
                        Integer diff = Math.abs(sequenceNumberACK - base);
                        for (int i = 0; i < diff; i++) {
                            windowPackets.remove();
                        }
                        base = sequenceNumberACK;
                        if (base ==  nextSeqNum){
                          timer.cancel();

                        }else{
                          timer.schedule(new resendWindowTask(),retryTimeout);
                        }
                    }
                }

            }
        }
    }

    static class resendWindowTask extends TimerTask {

        @Override
        public void run() {
            // send window packets, timer will start automatically
            ArrayList<DatagramPacket> packetstoAdd = new ArrayList<DatagramPacket>();
            synchronized (Sender2a.class) {
                while (!windowPackets.isEmpty()) {
                    // System.out.println(windowPackets.size());
                    DatagramPacket packet = windowPackets.remove();
                    try {
                        senderSocket.send(packet);
                        // System.out.println("Re-Sent");
                    } catch (IOException e) {
                      System.out.println(e);
                    }
                    packetstoAdd.add(packet);
                }
                // System.out.println("adding them back in the queue");
                for (int i=0;i<packetstoAdd.size();i++){
                  windowPackets.add(packetstoAdd.get(i));
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
    static Queue<DatagramPacket> windowPackets = new LinkedList<DatagramPacket>();
    static Boolean lastPacketAck = false;

    public static void main(String args[]) throws Exception {

        final String hostName = args[0];
        final int portNumber = Integer.parseInt(args[1]);
        final String fileName = args[2];
        retryTimeout = Integer.parseInt(args[3]);
        final int windowSize = Integer.parseInt(args[4]);
        sendFile(hostName, portNumber, fileName, windowSize);
    }

    public static void sendFile(String hostName, int portNumber, String fileName,
            int windowSize) throws IOException, Exception {

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
                        timer.schedule(new resendWindowTask(),retryTimeout);
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
