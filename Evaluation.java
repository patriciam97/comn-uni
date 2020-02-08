
import java.io.*;
import java.lang.Thread.State;
import java.net.*;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.util.Date;
public class Evaluation{

    public static void main(String args[]) throws Exception {
        HashMap<Integer,Double[]> results = experiment();
        System.out.println(results);
        
    }
    public static HashMap<Integer,Double[]> experiment() throws IOException {
        int[] retransmissions = {5,10,15,20,25,30,40,50,75,100};
        java.util.HashMap<Integer,Double[]> results = new HashMap<Integer,Double[]>();

        for (int i=retransmissions.length-1; i>0;i--){
            BufferedWriter file = new BufferedWriter(new FileWriter("results.txt", true));
            Date date = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd h:mm:ss a");
            file.write("\n=============================== Date: " + formatter.format(date)+"=============================== ");
            Integer timeout = retransmissions[i];
            System.out.println("Running tests for "+timeout);
            double counterRetransmissions = (double) 0;
            double throughput = (double) 0;
            for (int j=0;j<5;j++){
                file.write("\n=============================== Timeout : " + timeout +"=============================== ");
                try{
                    Receiver1bRunnable receiver = new Receiver1bRunnable(100, "1b.jpg");
                    Sender1bRunnable sender = new Sender1bRunnable("localhost", 100, "test.jpg", timeout);
                    Thread treceiver = new Thread(receiver);
                    Thread tsender = new Thread(sender);
                    treceiver.start();
                    tsender.start();
                    while (tsender.getState()== State.RUNNABLE){

                    }
                    file.write("\nTest "+ j +": Retransmissions: " + sender.getRetransmissions() +" Throughput: "+ sender.getThroughput());
                    counterRetransmissions+= sender.getRetransmissions();
                    throughput+=sender.getThroughput();
                    System.out.println("Experiment "+j+" for timeout "+timeout+"has been completed");
                    treceiver.stop();
                    tsender.stop();

                } catch (Exception e){
                    System.out.println(e.getMessage());
                }
                throughput/=5;
                Double[] res = {counterRetransmissions,throughput};
                results.put(timeout, res);

                
            }
            System.out.println("Test for "+timeout+" completed.");
            file.write("-------------------------------------------------------------------------------------------------");
            file.write("\nTimeout: "+timeout+"   Retransmissions: "+ counterRetransmissions+"    Avg.Throughput: "+throughput);
            file.write("-------------------------------------------------------------------------------------------------");
            file.close();
            System.out.println("Results:"+results.get(timeout));

        }
        return results;
    }

}