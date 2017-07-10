package ai_db_updater.iterators;

import ai_db_updater.handlers.CustomHandler;
import net.lingala.zip4j.core.ZipFile;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 7/6/2017.
 */
public class AssignmentIterator implements WebIterator {
    private File zipFolderPrefix;
    private String destinationPrefix;
    private AtomicInteger cnt;
    public AssignmentIterator(File zipFolderPrefix, String destinationPrefix) {
        this.zipFolderPrefix=zipFolderPrefix;
        this.destinationPrefix=destinationPrefix;
        this.cnt=new AtomicInteger(0);
    }

    @Override
    public void applyHandlers(CustomHandler... handlers) {
        Arrays.stream(zipFolderPrefix.listFiles()).parallel().forEach(zipFile->{
            String destinationFilename = destinationPrefix+cnt.getAndIncrement();
            // Ingest data for each file
            try {
                new ZipFile(zipFile).extractAll(destinationFilename);

                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(false);
                factory.setValidating(false);
                // security vulnerable
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                SAXParser saxParser = factory.newSAXParser();

                for (File file : new File(destinationFilename).listFiles()) {
                    if (!file.getName().endsWith(".xml")) {
                        file.delete();
                        continue;
                    }
                    try {
                        for(CustomHandler _handler : handlers) {
                            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                                saxParser.parse(bis, _handler.newInstance());
                            } catch (Exception e) {
                                System.out.println("Error ingesting file: " + file.getName());
                                e.printStackTrace();
                            }
                        }
                    } finally {
                        file.delete();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                File xmlFile = new File(destinationFilename);
                if (xmlFile.exists()) xmlFile.delete();
            }
        });

        for(CustomHandler handler : handlers) {
            handler.save();
        }
    }
}
