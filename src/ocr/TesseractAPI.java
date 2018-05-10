package ocr;

import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.lept;
import org.bytedeco.javacpp.tesseract;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class TesseractAPI {

    public static float pt2mm(float pt) {
        return pt * 25.4f / 72;
    }
    public static float mm2pt(float mm) {
        return mm / (25.4f / 72);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider"); // Do anything?
        long t0 = System.currentTimeMillis();

        PDFParser pdfParser = new PDFParser(new RandomAccessFile(new File("/Users/ehallmark/Downloads/12900011/12900011-image_file_wrapper/12900011-2010-10-07-00016-FOR.pdf"),"r"));
        pdfParser.parse();
        AtomicInteger pageCount = new AtomicInteger(0);
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
                            tesseract.TessBaseAPI api = new tesseract.TessBaseAPI();
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

        long t2 = System.currentTimeMillis();

        System.out.println("Time to complete: "+((t2-t0)/1000)+" seconds.");
    }
}
