package ocr;

import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.lept;
//import org.bytedeco.javacpp.tesseract;

import javax.imageio.ImageIO;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class TesseractAPI {
/*
   public static void main(String[] args) throws Exception {
       System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider"); // Do anything?
       long t0 = System.currentTimeMillis();

       File inputFile = new File("95000315/95000315-image_file_wrapper/95000315-2008-01-31-00002-RXLITSR.pdf");
       File outputFile = new File(inputFile.getAbsolutePath()+".txt");
       PDFParser pdfParser = new PDFParser(new RandomAccessFile(inputFile,"r"));
       pdfParser.parse();
       AtomicInteger pageCount = new AtomicInteger(0);
       BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
       tesseract.TessBaseAPI api = new tesseract.TessBaseAPI();
       AtomicBoolean save = new AtomicBoolean(false);
       pdfParser.getPDDocument().getPages().forEach(page->{
           PDResources resources = page.getResources();
           pageCount.getAndIncrement();
           AtomicInteger resourceCnt = new AtomicInteger(0);
           resources.getXObjectNames().forEach(n->resourceCnt.getAndIncrement());
           final int numResources = resourceCnt.get();
           if(numResources==1) {
               resources.getXObjectNames().forEach(objName -> {
                   resourceCnt.getAndIncrement();
                   if (resources.isImageXObject(objName)) {
                       try {
                           PDImageXObject imageObj = (PDImageXObject) resources.getXObject(objName);
                           // Initialize tesseract-ocr with English, without specifying tessdata path
                           if (api.Init(".", "ENG") != 0) {
                               System.err.println("Could not initialize tesseract.");
                               System.exit(1);
                           }
                           BytePointer outText;
                           // System.out.println("Writing image:" + image);

                           // OCR recognition
                           File tempFile = File.createTempFile("temp_images", "temp.png");
                           //ByteArrayOutputStream baos = new ByteArrayOutputStream();
                           ImageIO.write(imageObj.getImage(), "png", tempFile);
                           //byte[] imageBytes = baos.toByteArray();
                           //ByteBuffer imgBB = ByteBuffer.wrap(imageBytes);

                           //lept.PIX image = lept.pixReadMemPng(imgBB, imageBytes.length);
                           lept.PIX image = lept.pixRead(tempFile.getAbsolutePath());
                           api.SetImage(image);

                           // Get OCR result
                           outText = api.GetUTF8Text();
                           String string = outText.getString();

                           String[] content = string.split("\\s+");
                           String[] words = Stream.of(content).filter(c -> c.length() > 1).toArray(s -> new String[s]);

                           if (words.length > content.length / 2) {
                               System.out.println("Page contains text: " + pageCount.get());
                               save.set(true);
                               writer.write(string+"\n");
                           } else {
                               System.out.println("MOST LIKELY AN IMAGE: " + pageCount.get());
                           }
                           System.out.println("Ratio: " + ((double) words.length) / content.length);
                           // Destroy used object and release memory
                           api.End();
                           outText.deallocate();
                           lept.pixDestroy(image);
                           tempFile.delete();
                       } catch (Exception e) {
                           e.printStackTrace();
                       }
                   }
               });
           }
       });

       if(save.get()) {
           writer.flush();
           writer.close();
       } else if(outputFile.exists()) {
           outputFile.delete();
       }
        long t2 = System.currentTimeMillis();

        System.out.println("Time to complete: "+((t2-t0)/1000)+" seconds.");
    }*/
}
