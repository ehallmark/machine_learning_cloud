package pdf;

import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

public class PDFExtractor {

    public static String extractPDF(File pdfFile) throws IOException {
        PDFTextStripper pdfStripper = null;
        PDDocument pdDoc = null;
        COSDocument cosDoc = null;
        // PDFBox 2.0.8 require org.apache.pdfbox.io.RandomAccessRead
        RandomAccessFile randomAccessFile = new RandomAccessFile(pdfFile, "r");
        PDFParser parser = new PDFParser(randomAccessFile);
        parser.parse();
        cosDoc = parser.getDocument();
        pdfStripper = new PDFTextStripper();
        pdDoc = new PDDocument(cosDoc);
        pdfStripper.setStartPage(1);
        pdfStripper.setEndPage(5);
        return pdfStripper.getText(pdDoc);
    }
}
