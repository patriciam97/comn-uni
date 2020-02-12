import java.io.*;
import java.net.*;

public class Receiver1b {
    public static void main(String args[]) throws Exception {

        // Get the address, port and name of file to send over UDP
        final int port = Integer.parseInt(args[0]);
        final String fileName = args[1];

        receiveFile(port, fileName);
    }
    public static void sendAckPacket(int previousSequenceNumber, DatagramSocket receiverSocket, InetAddress hostAddress, int portNumber) throws IOException {
        // Resend acknowledgement
        byte[] ackPacketToSend = new byte[2];
        ackPacketToSend[0] = (byte)(previousSequenceNumber >> 8);
        ackPacketToSend[1] = (byte)(previousSequenceNumber);
        DatagramPacket acknowledgement = new  DatagramPacket(ackPacketToSend, ackPacketToSend.length, hostAddress, portNumber);
        receiverSocket.send(acknowledgement);
<<<<<<< HEAD
    }

    public static void receiveFile(int port, String fileName) throws Exception {
    //   create receiver socket
=======
        System.out.println("Sent: ACK Sequence Number = " + previousSequenceNumber);
    }

    public static void receiveFile(int port, String fileName) throws Exception {
      System.out.println("Waiting for file. . .");
      // create receiver socket
>>>>>>> f8e7d0953eb430adc27c21a33abc8cd4ea35a612
      DatagramSocket receiverSocket = new DatagramSocket(port);
      File file = new File(fileName);
      FileOutputStream fileStream = new FileOutputStream(file);

      //sequence numbers and flag
      int sequenceNumber = 0;
      int previousSequenceNumber = 0;
      boolean flagLastMessage = false;
      boolean lastMessage = false;
      // for each incoming message
      while (!lastMessage) {
<<<<<<< HEAD

=======
          // byte array for data (message without header)
          // byte array for full message (data + header)
>>>>>>> f8e7d0953eb430adc27c21a33abc8cd4ea35a612
          byte[] buffer = new byte[1027];
          // Receive packet and retrieve message
          DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
          receiverSocket.setSoTimeout(0);
          receiverSocket.receive(receivedPacket);

          // retrieve portNumber and hostAddress for sending ack back
          int portNumber = receivedPacket.getPort();
          InetAddress hostAddress = receivedPacket.getAddress();

<<<<<<< HEAD
=======

>>>>>>> f8e7d0953eb430adc27c21a33abc8cd4ea35a612
          byte[] messageReceived = new byte[receivedPacket.getLength()];
          byte[] dataReceived = new byte[receivedPacket.getLength() - 3];

          messageReceived = receivedPacket.getData();
<<<<<<< HEAD
          int sequenceNumberA = (messageReceived[0] & 0xff) << 8;
          int sequenceNumberB = (messageReceived[1] & 0xff);
          sequenceNumber = sequenceNumberA + sequenceNumberB;
          // duplicate detection at the receiver


          if ((previousSequenceNumber+1) ==  sequenceNumber) {
=======


          // duplicate detection at the receiver
          int sequenceNumberA = (messageReceived[0] & 0xff) << 8;
          int sequenceNumberB = (messageReceived[1] & 0xff);

          if ((previousSequenceNumber+1) ==  (sequenceNumberA + sequenceNumberB)) {
              sequenceNumber = (sequenceNumberA + sequenceNumberB);
>>>>>>> f8e7d0953eb430adc27c21a33abc8cd4ea35a612
              previousSequenceNumber = sequenceNumber;
              // check header to see if it's the last message
              if ((messageReceived[2] & 0xff) == 1) {
                  flagLastMessage = true;
              } else {
                  flagLastMessage = false;
              }
              // get data from message received

              for (int i=0; i < dataReceived.length; i++) {
                  dataReceived[i] = messageReceived[i+3];
              }

              // save data into a new file with name as fileName
              fileStream.write(dataReceived);
<<<<<<< HEAD
=======
              System.out.println("Received: Sequence number = " + sequenceNumber + " Flag = " + flagLastMessage + "   Length: "+ dataReceived.length);

>>>>>>> f8e7d0953eb430adc27c21a33abc8cd4ea35a612
              // Send acknowledgement
              sendAckPacket(previousSequenceNumber, receiverSocket, hostAddress, portNumber);

            } else {
<<<<<<< HEAD
                  //Resend the acknowledgement
                  sendAckPacket(previousSequenceNumber, receiverSocket, hostAddress, portNumber);
                  flagLastMessage = false;
=======
                  System.out.println("DISCARDED PACKET! Expected:  Sequence number: " + (previousSequenceNumber + 1) + " but received " + sequenceNumber + ".");
                  //Resend the acknowledgement
                  sendAckPacket(previousSequenceNumber, receiverSocket, hostAddress, portNumber);
                  flagLastMessage = false;
                  // throw new Exception("Corrupted packet"+sequenceNumber+" "+(sequenceNumberA + sequenceNumberB));
>>>>>>> f8e7d0953eb430adc27c21a33abc8cd4ea35a612
            }
              // if it was the last message to be received close file stream
              if (flagLastMessage) {
              fileStream.close();
              receiverSocket.close();
              lastMessage = true;
          }
      }

      // close socket once done
      receiverSocket.close();
      // confirmation message
<<<<<<< HEAD
      // System.out.println("\n=============================== C O M P L E T E D ===============================");
=======
      System.out.println("\n=============================== C O M P L E T E D ===============================");
>>>>>>> f8e7d0953eb430adc27c21a33abc8cd4ea35a612
      System.out.println("\n Received: "+fileName);
    }
}
