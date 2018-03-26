package scrape_patexia;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class Scraper {

    public static void main(String[] args) throws Exception {
        System.setProperty("jsse.enableSNIExtension", "false"); // VERY IMPORTANT!!!

        //final String username = "Evan Hallmark";
        //final String password = "Evan1234";

        File outputFile = new File("patexia-scrape.html.gz");
        final OutputStream outputStream = new GzipCompressorOutputStream(new FileOutputStream(outputFile));

        final int total = (100000/20)+1;
        AtomicInteger cnt = new AtomicInteger(0);
        IntStream.range(1,total).parallel().forEach(i->{
            System.out.println(cnt.getAndIncrement() + " / "+total);
            try {
                synchronized (outputStream) {
                    scrapePage(i, outputStream);
                }
                //TimeUnit.MILLISECONDS.sleep(10);
            } catch(Exception e) {
                e.printStackTrace();
            }
        });

        outputStream.close();
    }

    private static void scrapePage(int page, OutputStream outputStream) throws Exception {
        URL url = new URL("https://www.patexia.com/ip-research/lawsuits/page/"+page);
        HttpBasicAuth.downloadFileWithAuth(url,outputStream);
    }
}
