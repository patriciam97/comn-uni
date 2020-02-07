
import java.io.*;
import java.net.*;

public class Evaluation {

    public static void main(String args[]) throws Exception {

        
    }
    public static void experiment() throws IOException {
        int[] retransmissions = {5,10,15,20,25,30,40,50,75,100};
        for (int i=0; i< retransmissions.length;i++){
            int timeout = retransmissions[i];
            for (int j=0;j<5;j++){
                String rec[] = {"100","1a.jpg"};
                String sen[]= {"localhost","100","test.jpg",Integer.toString(timeout)};
                try{
                    Receiver1a receiver = new Receiver1a();
                    receiver.main(rec);
                    Sender1a sender = new Sender1a();
                    sender.main(sen);
                } catch (Exception e){
                    System.out.println(e.getMessage());
                }

            }
        }
        
    }

}