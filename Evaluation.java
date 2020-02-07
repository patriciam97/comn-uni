
import java.io.*;
import java.lang.Thread.State;
import java.net.*;
import java.util.HashMap;
public class Evaluation{

    public static void main(String args[]) throws Exception {
        HashMap<Integer,Double[]> results = experiment();
        System.out.println(results);
        
    }
    public static HashMap<Integer,Double[]> experiment() throws IOException {
        int[] retransmissions = {5,10,15,20,25,30,40,50,75,100};
        java.util.HashMap<Integer,Double[]> results = new HashMap<Integer,Double[]>();

        for (int i=retransmissions.length-1; i>0;i--){
            Integer timeout = retransmissions[i];
            System.out.println("Running test for "+timeout);
            double counterRetransmissions = (double) 0;
            double throughput = (double) 0;
            for (int j=0;j<5;j++){
                String rec[] = {"100","1a.jpg"};
                try{
                    Receiver1bRunnable receiver = new Receiver1bRunnable(100, "1b.jpg");
                    Sender1bRunnable sender = new Sender1bRunnable("localhost", 100, "test.jpg", timeout);
                    Thread treceiver = new Thread(receiver);
                    Thread tsender = new Thread(sender);
                    treceiver.start();
                    tsender.start();
                    while (tsender.getState()== State.RUNNABLE){

                    }
                    counterRetransmissions+= sender.getRetransmissions();
                    throughput+=sender.getThroughput();
                    System.out.println("Experiment "+j+" for timeout "+timeout+"has been completed");

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