import java.io.*;
import java.net.*;
import java.util.*;


public class Sender2b {

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
                synchronized (Sender2b.class) {
                      Integer sequenceNumberACK = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                      maxACKpacket = Math.max(maxACKpacket,sequenceNumberACK);
                      System.out.println("Received: " + sequenceNumberACK + " " + finalPacketId);
                      if (timerTasks.containsKey(sequenceNumberACK)) {
                          timerTasks.get(sequenceNumberACK).cancel();
                          System.out.println("Timer stopped: " + sequenceNumberACK);
                          timerTasks.remove(sequenceNumberACK);
                      }
                      if (timerTasks.size()==0 && maxACKpacket == finalPacketId-1){
                        lastPacketAck = true;
                        timer.cancel();
                      }
                      // base = sequenceNumberACK;
                      if (base == sequenceNumberACK){
                          if (!timerTasks.isEmpty()){
                              base = Collections.min(timerTasks.keySet());
                          } else {
                              base = nextSeqNum;
                          }
                      }
                }

            }
        }
    }

    static class resendPacketTask extends TimerTask {
        Integer sequenceNumber;
        DatagramPacket packet;

        resendPacketTask(Integer sequenceNumber, DatagramPacket packet) {
            this.sequenceNumber = sequenceNumber;
            this.packet = packet;
        }

        @Override
        public void run() {
            // send window packets, timer will start automatically
            try {
                senderSocket.send(this.packet);
                System.out.println("Re-sent: "+this.sequenceNumber);
            } catch (IOException e) {
                System.err.println(e);
            }
        }
    }

    static int base = 0;
    static int nextSeqNum = 0;
    static Integer retryTimeout = 0;
    static int finalPacketId = 0;
    static DatagramSocket senderSocket;
    static Timer timer = new Timer();
    static HashMap<Integer, TimerTask> timerTasks = new HashMap<Integer, TimerTask>();
    static Boolean lastPacketAck = false;
    static Integer maxACKpacket = -1;

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

        while (!lastPacketAck) {
            // // // System.out.println("Base: " + base + ", NextSeqNum: " + nextSeqNum + "
            // // // Window: " + windowSize);
            // if (nextSeqNum==7){
            //   Thread.sleep(10000);
            // }
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
                    TimerTask task = new resendPacketTask(nextSeqNum, packetToSend);
                    timerTasks.put(nextSeqNum, task);
                    timer.schedule(task,0,retryTimeout);
                    System.out.println("Sent: Sequence number = " + nextSeqNum + " Flag = " +
                    flagLastMessage
                    + " Length: " + messageToSend.length);
                    nextSeqNum += 1;
                }
            }

        }
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
