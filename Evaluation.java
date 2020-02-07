
import java.io.*;
import java.net.*;
import java.util.HashMap;
public class Evaluation {

    public static void main(String args[]) throws Exception {
        HashMap<Integer,Double[]> results = experiment();
        System.out.println(results);
        
    }
    public static HashMap<Integer,Double[]> experiment() throws IOException {
        int[] retransmissions = {5,10,15,20,25,30,40,50,75,100};
        java.util.HashMap<Integer,Double[]> results = new HashMap<Integer,Double[]>();

        for (int i=0; i< retransmissions.length;i++){
            Integer timeout = retransmissions[i];
            System.out.println("Running test for "+timeout);
            double counterRetransmissions = (double) 0;
            double throughput = (double) 0;
            for (int j=0;j<5;j++){
                String rec[] = {"100","1a.jpg"};
                String sen[]= {"localhost","100","test.jpg",Integer.toString(timeout)};
                try{
                    Receiver1a receiver = new Receiver1a();
                    receiver.main(rec);
                    Sender1b sender = new Sender1b();
                    double[] res = sender.main(sen);
                    counterRetransmissions+=res[0];
                    throughput+=res[1];

                } catch (Exception e){
                    System.out.println(e.getMessage());
                }
                throughput/=5;
                Double[] res = {counterRetransmissions,throughput};
                results.put(timeout, res);
            }
            System.out.println("Test for "+timeout+" completed.");
            System.out.println("Results:"+results.get(timeout));


        }
        return results;
    }

}