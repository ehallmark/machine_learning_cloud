package pdf;

import com.snowtide.PDF;
import com.snowtide.pdf.Document;
import com.snowtide.pdf.OutputTarget;

import java.io.File;
import java.io.IOException;

public class PDFExtractor {

    public static String extractPDF(File pdfFile) throws IOException {
        String pdfFilePath = pdfFile.getAbsolutePath();
        Document pdf = PDF.open(pdfFilePath);
        StringBuilder text = new StringBuilder(1024);
        pdf.pipe(new OutputTarget(text));
        pdf.close();
        return text.toString();
    }
}
