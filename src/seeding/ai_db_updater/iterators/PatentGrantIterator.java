package seeding.ai_db_updater.iterators;

import seeding.ai_db_updater.handlers.CustomHandler;
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
public class PatentGrantIterator implements WebIterator {
    public AtomicInteger cnt;
    private File zipFileFolder;
    private String destinationPrefix;
    public PatentGrantIterator(File zipFileFolder, String destinationPrefix) {
        this.cnt=new AtomicInteger(0);
        this.zipFileFolder=zipFileFolder;
        this.destinationPrefix=destinationPrefix;
    }

    @Override
    public void applyHandlers(CustomHandler... handlers) {
        Arrays.stream(zipFileFolder.listFiles()).parallel().forEach(zipFile->{
            final String destinationFilename = destinationPrefix + cnt.getAndIncrement();
            try {
                // Unzip file
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(zipFile));
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(destinationFilename)));
                ZipHelper.unzip(bis, bos);
                bis.close();
                bos.close();

                File xmlFile = new File(destinationFilename);
                if (xmlFile.exists()) {
                    System.out.println("Success!");

                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    factory.setNamespaceAware(false);
                    factory.setValidating(false);
                    // security vulnerable
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                    SAXParser saxParser = factory.newSAXParser();

                    FileReader fr = new FileReader(xmlFile);
                    BufferedReader br = new BufferedReader(fr);
                    String line;
                    boolean firstLine = true;
                    List<String> lines = new ArrayList<>();
                    while ((line = br.readLine()) != null) {
                        if (line.contains("<?xml") && !firstLine) {
                            // stop
                            byte[] data = String.join("", lines).getBytes();
                            for (CustomHandler _handler : handlers) {
                                CustomHandler handler = _handler.newInstance();
                                try {
                                    saxParser.parse(new ByteArrayInputStream(data), handler);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    handler.reset();
                                }
                            }
                            lines.clear();
                        }
                        if (firstLine) firstLine = false;
                        lines.add(line);
                    }
                    br.close();
                    fr.close();

                    // get the last one
                    if (!lines.isEmpty()) {
                        byte[] data = String.join("", lines).getBytes();
                        for (CustomHandler _handler : handlers) {
                            CustomHandler handler = _handler.newInstance();
                            try {
                                saxParser.parse(new ByteArrayInputStream(data), handler);
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                handler.reset();
                            }
                        }
                        lines.clear();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();

            } finally {
                // cleanup
                File xmlFile = new File(destinationFilename);
                if (xmlFile.exists()) xmlFile.delete();
            }

        });

        // save
        for(CustomHandler handler : handlers) {
            handler.save();
        }

    }

}
