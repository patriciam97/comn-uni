
import java.io.*;
import java.net.*;
import java.util.Date;
public class Sender1b {
    public static double[] main(String args[]) throws Exception {

      final String hostName = args[0];
      final int portNumber = Integer.parseInt(args[1]);
      final String fileName = args[2];
      final int timeout = Integer.parseInt(args[3]);
      return sendFile(hostName,portNumber,fileName,timeout);
    }

    public static double[] sendFile(String hostName, int portNumber, String fileName,int timeout) throws IOException {

      // create sender socket
      DatagramSocket senderSocket = new DatagramSocket();
      // translate hostName to an IP address using DNS
      InetAddress ipAddress = InetAddress.getByName(hostName);
      // read file from fileName
      File file = new File(fileName);
      // convert file into a stream of bytes
      InputStream fileStream = new FileInputStream(file);
      // create a byte array to split the fileStream into packets later
      byte[] fileByteArray = new byte[(int)file.length()];
      System.out.println("Byte array of size "+ (int)file.length()+" has been created.");
      // // split the stream of bytes into a byte array
      fileStream.read(fileByteArray);
      // timer needed to calculate avg throughput at the end
      // Timer timer = new Timer(0);
      Date date = new Date();
      long timeMilli = date.getTime();
      // sequence number and flag will be needed for the header of each packet
      int sequenceNumber = -1;
      boolean flagLastMessage = false;
      // sequence number to keep track the acknowledged packets
      int sequenceNumberACK = -1;
      // counter for retransmissions
      int retransmissionCounter = 0;
      // for each message that is being generated

      for (int i=0; i < fileByteArray.length; i +=1024 ) { //1KB = 1024 bytes  - 3 bytes for header = 1021
          sequenceNumber += 1;
          // byte array of all packets
          byte[] messageToSend = new byte[1027];
          // duplicate sequence number in header will be used to check for corrupted packets
          messageToSend[0] = (byte)(sequenceNumber >> 8);
          messageToSend[1] = (byte)(sequenceNumber);
          // check if this packet is the last packet
          if ((i+1024) >= fileByteArray.length) {
              // set flagLastMessage to 1 if it's the last packet to send
              flagLastMessage = true;
              // add it in the header
              messageToSend[2] = (byte)(1);
          } else {
              flagLastMessage = false;
              messageToSend[2] = (byte)(0);
          }
        // append message bytes
        if (!flagLastMessage) {
            for (int j=0; j <= 1023; j++) {
              messageToSend[j+3] = fileByteArray[i+j];
            }
        } else if (flagLastMessage) {
          // append whatever is left
          messageToSend = new byte[(fileByteArray.length - i) + 3];
            for (int j=0;  j < (fileByteArray.length - i) ;j++) {
              messageToSend[j+3] = fileByteArray[i+j];
            }
            messageToSend[0] = (byte)(sequenceNumber >> 8);
            messageToSend[1] = (byte)(sequenceNumber);
            messageToSend[2] = (byte)(1);
        }

        DatagramPacket packetToSend = new DatagramPacket(messageToSend, messageToSend.length, ipAddress, portNumber);
        senderSocket.send(packetToSend);
        System.out.println("Sent: Sequence number = " + sequenceNumber + "    Flag = " + flagLastMessage + "   Length: "+messageToSend.length);

        // verifying acknowledgements
        boolean ackRecievedCorrect = false;
        boolean ackPacketReceived = false;

        while (!ackRecievedCorrect) {
            // Check for an ack
            byte[] ack = new byte[2];
            DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);

            try {
                senderSocket.setSoTimeout(timeout);
                senderSocket.receive(ackPacket);
                sequenceNumberACK = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                ackPacketReceived = true;
            } catch (SocketTimeoutException e) {
                System.out.println("Ack missing:   socket timed out");
                ackPacketReceived = false;
                //e.printStackTrace();
            }

            // Break if there is an ack so that the next packet can be sent
            if ((sequenceNumberACK == sequenceNumber) && (ackPacketReceived)) {
                ackRecievedCorrect = true;
                System.out.println("Ack received: Sequence Number = " + sequenceNumberACK);
                break;
            } else { // Resend packet
                senderSocket.send(packetToSend);
                System.out.println("Resending: Sequence Number = " + sequenceNumber);

                // Increment retransmission counter
                retransmissionCounter += 1;
            }
           }

          // 10ms gap after each packet transmission to avoid overflow of queue
            //   try {
            //       Thread.sleep(10);
            //   } catch (InterruptedException e) {
            //       e.printStackTrace();
            //   }

      }
      senderSocket.close();
      fileStream.close();
      System.out.println("\n============================== C O M P L E T E D ===============================");
      System.out.println("\nSent: " + fileName);
      System.out.println("To: " + hostName+":"+portNumber);
      // Calculate the average throughput
      int fileSizeKB = (fileByteArray.length) / 1027;
      date = new Date();
      long timeDoneMS = date.getTime();
      double transferTime = (timeDoneMS - timeMilli)/ 1000;
      double throughput = (double) fileSizeKB / transferTime;
      System.out.println("File Size: " + fileSizeKB + " KB");
      System.out.println("Transfer Time: " + transferTime + " seconds");
      System.out.println("Throughput: " + throughput + " KBps");
      System.out.println("Number of retransmissions: " + retransmissionCounter);
      double[] results = {new Double(retransmissionCounter),throughput};
      return (results);
    }
  }
