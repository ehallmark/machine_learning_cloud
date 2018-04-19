package seeding.ai_db_updater.iterators;

import lombok.Getter;
import lombok.NonNull;
import seeding.ai_db_updater.handlers.CustomHandler;
import seeding.ai_db_updater.tools.ZipHelper;
import seeding.data_downloader.FileStreamDataDownloader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Created by Evan on 7/5/2017.
 */
public class ZipFileIterator implements WebIterator {
    public AtomicInteger cnt;
    private String destinationPrefix;
    private FileStreamDataDownloader dataDownloader;
    private boolean parallel;
    private boolean perDocument;
    private final Function<File,Boolean> orFunction;
    private boolean testing;
    @Getter
    private File currentlyIngestingFile;
    private Function<File,File> destinationToFileFunction;
    public ZipFileIterator(FileStreamDataDownloader dataDownloader, String destinationPrefix, boolean parallel, boolean perDocument, @NonNull Function<File,Boolean> orFunction, boolean testing, Function<File,File> destinationToFileFunction) {
        this.cnt=new AtomicInteger(0);
        this.dataDownloader=dataDownloader;
        this.destinationPrefix=destinationPrefix;
        this.parallel=parallel;
        this.destinationToFileFunction=destinationToFileFunction;
        this.perDocument=perDocument;
        this.testing=testing;
        this.orFunction=orFunction;
    }

    public ZipFileIterator(FileStreamDataDownloader dataDownloader, String destinationPrefix, boolean parallel, boolean perDocument, @NonNull Function<File,Boolean> orFunction, boolean testing) {
        this(dataDownloader,destinationPrefix,parallel,perDocument,orFunction,testing,file->file);
    }

    public ZipFileIterator(FileStreamDataDownloader dataDownloader, String destinationPrefix, boolean testing) {
        this(dataDownloader,destinationPrefix,true, true,file->false,testing);
    }

    @Override
    public void applyHandlers(CustomHandler... handlers) {
        // pull latest data
        if(!testing) {
            dataDownloader.pullMostRecentData();
            dataDownloader.save();
        }
        List<File> fileStream = dataDownloader.zipFileStream(orFunction).sorted(Comparator.comparing(e->e.getName())).collect(Collectors.toList());
        (parallel ? fileStream.parallelStream() : fileStream.stream()).forEach(zipFile->{
            final String destinationFilename = destinationPrefix + zipFile.getName();
            AtomicBoolean failed = new AtomicBoolean(false);
            try {
                System.out.print("Starting to unzip: "+zipFile.getName()+"...");
                // Unzip file
                {
                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(zipFile));
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(destinationFilename)));
                    ZipHelper.unzip(bis, bos);
                    bis.close();
                    bos.flush();
                    bos.close();
                }

                File xmlFile = destinationToFileFunction.apply(new File(destinationFilename));
                if (xmlFile.exists()) {
                    if(!parallel)currentlyIngestingFile=xmlFile;
                    System.out.println("Parsing "+xmlFile.getName()+" now...");
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    factory.setNamespaceAware(true);
                    factory.setValidating(false);
                    // security vulnerable
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                    SAXParser saxParser = factory.newSAXParser();

                    FileReader fr = new FileReader(xmlFile);
                    BufferedReader br = new BufferedReader(fr);

                    if(perDocument) {
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
                                        failed.set(true);
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
                                    failed.set(true);
                                }
                            }
                            lines.clear();
                        }
                    } else {
                        for (CustomHandler handler : handlers) {
                            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(xmlFile))) {
                                saxParser.parse(bis, handler.newInstance());
                            } catch (Exception e) {
                                failed.set(true);
                                e.printStackTrace();
                            }
                        }
                    }

                    if(!failed.get()) {
                        System.out.println(" Parsed successfully!");
                        if(!testing) {
                            dataDownloader.finishedIngestingFile(zipFile);
                        }
                    }
                } else {
                    failed.set(true);
                }

            } catch (Exception e) {
                failed.set(true);
                e.printStackTrace();
            } finally {
                // cleanup
                if(failed.get()) {
                    if(!testing) {
                        dataDownloader.errorOnFile(zipFile);
                    }
                }

                File xmlFile = new File(destinationFilename);
                if (xmlFile.exists()) xmlFile.delete();
            }

        });

        // save
        if(!testing) {
            for (CustomHandler handler : handlers) {
                handler.save();
            }
            dataDownloader.save();
        }
    }

}
