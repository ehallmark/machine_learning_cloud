package seeding.ai_db_updater.iterators;

import seeding.ai_db_updater.handlers.CustomHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.tools.ZipHelper;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by Evan on 7/5/2017.
 */
public class FileIterator implements WebIterator {
    public AtomicInteger cnt;
    private File fileFolder;
    private FilenameFilter filter;
    public FileIterator(File fileFolder, FilenameFilter filter) {
        this.cnt=new AtomicInteger(0);
        this.fileFolder=fileFolder;
        this.filter=filter;
    }

    @Override
    public void applyHandlers(CustomHandler... handlers) {
        Arrays.stream(fileFolder.listFiles(filter)).parallel().forEach(xmlFile->{
            try {
                System.out.println("Parsing "+xmlFile.getName()+" now...");
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setValidating(false);
                // security vulnerable
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                SAXParser saxParser = factory.newSAXParser();
                if (xmlFile.exists()) {
                    for (CustomHandler handler : handlers) {
                        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(xmlFile))) {
                            saxParser.parse(bis, handler.newInstance());
                            System.gc();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                System.out.println(" Parsed successfully!");

            } catch (Exception e) {
                e.printStackTrace();

            }

        });

        // save
        for(CustomHandler handler : handlers) {
            handler.save();
        }
    }

}
