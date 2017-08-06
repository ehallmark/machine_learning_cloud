package pair_bulk_data;

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
    public FileIterator(File fileFolder) {
        this.cnt=new AtomicInteger(0);
        this.fileFolder=fileFolder;
    }

    @Override
    public void applyHandlers(CustomHandler... handlers) {
        Arrays.stream(fileFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                try {
                    return Integer.valueOf(name.substring(0,4)) >= 2005;
                } catch(Exception e) {
                    return false;
                }
            }
        })).parallel().forEach(xmlFile->{
            try {
                if (xmlFile.exists()) {
                    System.out.print("Parsing "+xmlFile.getName()+" now...");
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    factory.setNamespaceAware(true);
                    factory.setValidating(false);
                    // security vulnerable
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);

                    for (CustomHandler handler : handlers) {
                        SAXParser saxParser = factory.newSAXParser();
                        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(xmlFile))) {
                            saxParser.parse(bis, handler.newInstance());
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            handler.save();
                        }
                    }
                }

                System.out.println(" Parsed successfully!");

            } catch (Exception e) {
                e.printStackTrace();

            }

        });


    }

}
