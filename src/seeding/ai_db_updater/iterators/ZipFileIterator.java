package seeding.ai_db_updater.iterators;

import seeding.ai_db_updater.handlers.CustomHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.tools.ZipHelper;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * Created by Evan on 7/5/2017.
 */
public class ZipFileIterator implements WebIterator {
    public AtomicInteger cnt;
    private String destinationPrefix;
    private File zipFolder;
    private FilenameFilter filter;
    public ZipFileIterator(File zipFolder, String destinationPrefix, FilenameFilter filter) {
        this.cnt=new AtomicInteger(0);
        this.destinationPrefix=destinationPrefix;
        this.zipFolder=zipFolder;
        this.filter=filter;
    }

    @Override
    public void applyHandlers(CustomHandler... handlers) {
        Arrays.stream(zipFolder.listFiles(filter)).parallel().forEach(zipFile->{
            final String destinationFilename = destinationPrefix + cnt.getAndIncrement();
            try {
                System.out.print("Starting to unzip: "+zipFile.getName()+"...");
                // Unzip file
                {
                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(zipFile));
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(destinationFilename)));
                    ZipHelper.unzip(bis, bos);
                    bis.close();
                    bos.close();
                }

                File xmlFile = new File(destinationFilename);
                if (xmlFile.exists()) {
                    System.out.print("Parsing "+xmlFile.getName()+" now...");
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    factory.setNamespaceAware(false);
                    factory.setValidating(false);
                    // security vulnerable
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);

                    for (CustomHandler handler : handlers) {
                        SAXParser saxParser = factory.newSAXParser();
                        try(BufferedReader reader = new BufferedReader(new FileReader(xmlFile))) {
                            InputStream stream = new InputStream() {
                                Iterator<Integer> lineStream = reader.lines().filter(line -> !line.contains("<?")).flatMapToInt(line -> line.chars()).iterator();

                                @Override
                                public int read() throws IOException {
                                    if (lineStream.hasNext()) return lineStream.next();
                                    else return 0;
                                }
                            };
                            saxParser.parse(stream, handler.newInstance());

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
            } finally {
                // cleanup
                File xmlFile = new File(destinationFilename);
                if (xmlFile.exists()) xmlFile.delete();
                System.out.println();
            }

        });


    }

}
