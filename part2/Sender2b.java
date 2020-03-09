import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

class AckReceive implements Callable<Integer> {

    DatagramSocket senderSocket;
    @Override
    public Integer call() throws Exception {
            byte[] ack = new byte[2];
            DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
            this.senderSocket.receive(ackPacket);
            Integer sequenceNumberACK = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
            return sequenceNumberACK;
    }
}

class PacketSendThread implements Runnable { 
    // to stop the thread 
    private boolean exit; 
    private Integer sequenceNumber; 
    private DatagramSocket senderSocket; 
    private int timeout; 
    private DatagramPacket packet; 
    Thread t; 

    PacketSendThread(final Integer sequenceNumber, final DatagramSocket senderSocket,
    int timeout, DatagramPacket packet) 
    { 
        this.sequenceNumber = sequenceNumber; 
        this.senderSocket = senderSocket;
        this.timeout = timeout;
        this.packet = packet;
        t = new Thread(this, Integer.toString(sequenceNumber)); 
        System.out.println("New thread: " + t); 
        exit = false; 
        t.start(); // Starting the thread 
    } 
    public void run() 
    { 
        int i = 0; 
        while (!exit) { 
            try {
                this.senderSocket.send(this.packet);
                System.out.println("Sent: Sequence number = " + this.sequenceNumber);
                Thread.sleep(this.timeout);

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        } 
        System.out.println(Integer.toString(sequenceNumber) + " Stopped."); 
    } 
    public void stop() 
    { 
        exit = true; 
    } 
} 

public class Sender2b {

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
        int base = 0;
        int nextSeqNum = 0;
        int finalPacketId = (int) Math.ceil((double) file.length() / 1024);
        boolean lastPacketAck = false;
        // time needed to calculate avg throughput at the end
        Date date = new Date();
        long timeStartedSendingMS = date.getTime();
        ExecutorService executor = null;
        Future<Integer> futureAck = null;
        // Thread waiting to receive things is always running in the background
        executor = Executors.newCachedThreadPool();
        AckReceive ackReceive = new AckReceive();
        ackReceive.senderSocket = senderSocket;
        futureAck = executor.submit(ackReceive);
        HashMap<Integer, DatagramPacket> packetsSentBuffer = new HashMap<Integer, DatagramPacket>();
        HashMap<Integer, PacketSendThread> threads = new HashMap<Integer, PacketSendThread>();

        while (!lastPacketAck) {
            // System.out.println("Base: " + base + ", NextSeqNum: " + nextSeqNum + "
            // Window: " + windowSize);
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
                PacketSendThread threadToRun = new PacketSendThread(nextSeqNum, senderSocket, retryTimeout, packetToSend);
                threads.put(nextSeqNum,threadToRun);

                packetsSentBuffer.put(nextSeqNum, packetToSend);
                nextSeqNum += 1;
            }
            if (futureAck.isDone() && !lastPacketAck) {
                Integer result = futureAck.get();
                sequenceNumberACK = result;
                futureAck = executor.submit(ackReceive);
                PacketSendThread threadToStop = threads.get(sequenceNumberACK);
                threadToStop.stop();
                    // if (base == nextSeqNum) {
                    //     futureAck.cancel(true);
                    // } else {
                    //     futureAck = executor.submit(ackReceive);
                    // }
                if (sequenceNumberACK == finalPacketId - 1) {
                    futureAck.cancel(true);
                    lastPacketAck = true;
                    break;
                }
            } else {
                    futureAck = executor.submit(ackReceive);
                    int max = base + windowSize;
                    if ((base + windowSize) > finalPacketId) {
                        // System.out.println("here max is "+ finalPacketId);
                        max = finalPacketId;
                    }
                    byte[] messageToSend = new byte[1027];
                    for (int i = base; i < max; i++) {
                        if ((i * 1024) + 1024 >= fileByteArray.length) {
                            messageToSend = new byte[fileByteArray.length - (i * 1024) + 3];
                            flagLastMessage = true;
                        } else {
                            flagLastMessage = false;
                        }
                        messageToSend[0] = (byte) (i >> 8);
                        messageToSend[1] = (byte) (i);

                        if (flagLastMessage) {
                            messageToSend[2] = (byte) 1;
                            for (int j = 0; j < (fileByteArray.length - (i * 1024)); j++) {
                                messageToSend[j + 3] = fileByteArray[(i*1024) + j];
                            }
                        } else {
                            messageToSend[2] = (byte) 0;
                            for (int j = 0; j <= 1023; j++) {
                                messageToSend[j + 3] = fileByteArray[(i*1024) + j];
                            }
                        }
                        DatagramPacket packetToSend = new DatagramPacket(messageToSend, messageToSend.length, ipAddress,
                                portNumber);
                        senderSocket.send(packetToSend);
                        System.out.println("Re-Sent: Sequence number = " + i + " Flag = " +flagLastMessage+ " Length: " + messageToSend.length);
                    }
                }
            }
        }
        executor.shutdown();
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