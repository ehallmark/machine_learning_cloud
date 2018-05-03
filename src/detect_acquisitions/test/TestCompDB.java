package detect_acquisitions.test;

import detect_acquisitions.PhantomJS;

import java.io.*;

public class TestCompDB {
    public static void main(String[] args) throws Exception {
        File file = new File("compdb.csv");
        BufferedWriter writer = new BufferedWriter(new FileWriter("compdb_results.csv"));
        writer.write("Deal ID,Seller,Buyer,Actual Acquisition,Predicted Acquisition\n");
        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.lines().skip(1).forEach(line->{
                String[] cells = line.split(",");
                if(cells.length>=4) {
                    String dealId = cells[0];
                    String status = cells[1];
                    String seller = cells[2];
                    String buyer = cells[3];
                    // test
                    if(seller.length()>0&&buyer.length()>0) {
                        boolean isAcq = status.toLowerCase().startsWith("acq");
                        boolean prediction = PhantomJS.test(seller, buyer);
                        if(isAcq==prediction) System.out.println("CORRECT!!!");
                        else System.out.println("false!!");

                        try {
                            writer.write(dealId + "," + seller + "," + buyer + "," + isAcq + "," + prediction + "\n");
                        } catch(Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                writer.flush();
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.exit(0);
    }
}
