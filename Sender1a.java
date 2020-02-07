
import java.io.*;
import java.net.*;

public class Sender1a {

    public static void main(String args[]) throws Exception {

      final String hostName = args[0];
      final int portNumber = Integer.parseInt(args[1]);
      final String fileName = args[2];

      sendFile(hostName,portNumber,fileName);
    }

    public static void sendFile(String hostName, int portNumber, String fileName) throws IOException {
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

      // sequence number and flag will be needed for the header of each packet
      int sequenceNumber = -1;
      boolean flagLastMessage = false;

      // for each message that is being generated
      for (int i=0; i < fileByteArray.length; i +=1024 ) { //1KB = 1024 bytes  - 3 bytes for header = 1021

        sequenceNumber += 1;
        // byte array of all packets
        byte[] messageToSend = new byte[1027];
        // byte[] lastMessageToSend;
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
        System.out.println("Sent: Sequence number = " + sequenceNumber + "   Flag = " + flagLastMessage  + "   Length: "+messageToSend.length);

        // 10ms gap after each packet transmission to avoid overflow of queue
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

    }
    senderSocket.close();
    fileStream.close();
    System.out.println("\n=============================== C O M P L E T E D ===============================");
    System.out.println("\nSent: " + fileName);
    System.out.println("To: " + hostName+":"+portNumber);
  }
}
