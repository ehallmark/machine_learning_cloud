package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.ClaimDataSAXHandler;
import seeding.ai_db_updater.tools.ZipHelper;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.RecursiveAction;

/**
 * Created by ehallmark on 1/3/17.
 */
public class UpdateClaimScoreData {
    private static final String ZIP_FILE_NAME = "tmp_uspto_zip_file_claim_data.zip";
    private static final String DESTINATION_FILE_NAME = "uspto_xml_file_claim_data.xml";

    public static void main(String[] args) {
        Map<String,Double> patentToIndependentClaimRatioMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Integer> patentToIndependentClaimLengthMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Double> patentToMeansPresentRatioMap = Collections.synchronizedMap(new HashMap<>());
        try {
            final int numTasks = 124;
            List<RecursiveAction> tasks = new ArrayList<>(numTasks);
            // Get last ingested date
            Integer lastIngestedDate = 60000;
            LocalDate date = LocalDate.now();
            String endDateStr = String.valueOf(date.getYear()).substring(2,4)+String.format("%02d",date.getMonthValue())+String.format("%02d",date.getDayOfMonth());
            Integer endDateInt = Integer.valueOf(endDateStr);

            System.out.println("Starting with date: "+lastIngestedDate);
            System.out.println("Ending with date: "+endDateInt);
            String base_url = "http://storage.googleapis.com/patents/grant_full_text";
            String secondary_url = "https://bulkdata.uspto.gov/data2/patent/grant/redbook/fulltext";
            while(lastIngestedDate<=endDateInt) {
                // Commit results to DB and update last ingest table
                lastIngestedDate = lastIngestedDate+1;
                // don't over search days
                if(lastIngestedDate%100 > 31) {
                    lastIngestedDate = lastIngestedDate+100 - (lastIngestedDate%100);
                }
                if(lastIngestedDate%10000 > 1231) {
                    lastIngestedDate = lastIngestedDate+10000 - (lastIngestedDate%10000);
                }

                final int finalLastIngestedDate=lastIngestedDate;


                // Load file from Google
                RecursiveAction action = new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            try {
                                String dateStr = String.format("%06d", finalLastIngestedDate);
                                URL website = new URL(base_url + "/20" + dateStr.substring(0, 2) + "/ipg" + String.format("%06d", finalLastIngestedDate) + ".zip");
                                System.out.println("Trying: " + website.toString());
                                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                                FileOutputStream fos = new FileOutputStream(ZIP_FILE_NAME + finalLastIngestedDate);
                                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                fos.close();

                                try {
                                    // Unzip file
                                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(ZIP_FILE_NAME + finalLastIngestedDate)));
                                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(DESTINATION_FILE_NAME + finalLastIngestedDate)));
                                    ZipHelper.unzip(bis, bos);
                                    bis.close();
                                    bos.close();
                                } catch (Exception e) {
                                    System.out.println("Unable to unzip google file");
                                }
                            } catch (Exception e) {
                                // try non Google
                                try {
                                    String dateStr = String.format("%06d", finalLastIngestedDate);
                                    URL website = new URL(secondary_url + "/20" + dateStr.substring(0, 2) + "/ipg" + String.format("%06d", finalLastIngestedDate) + ".zip");
                                    System.out.println("Trying: " + website.toString());
                                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                                    FileOutputStream fos = new FileOutputStream(ZIP_FILE_NAME + finalLastIngestedDate);
                                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                    fos.close();
                                } catch (Exception e2) {
                                    System.out.println("Not found");
                                    return;
                                }

                                try {
                                    // Unzip file
                                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(ZIP_FILE_NAME + finalLastIngestedDate)));
                                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(DESTINATION_FILE_NAME + finalLastIngestedDate)));
                                    ZipHelper.unzip(bis, bos);
                                    bis.close();
                                    bos.close();
                                } catch (Exception e2) {
                                    System.out.println("Unable to unzip file");
                                    return;
                                }
                            }


                            // Ingest data for each file
                            try {

                                SAXParserFactory factory = SAXParserFactory.newInstance();
                                factory.setNamespaceAware(false);
                                factory.setValidating(false);
                                // security vulnerable
                                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                                SAXParser saxParser = factory.newSAXParser();

                                ClaimDataSAXHandler handler = new ClaimDataSAXHandler();


                                FileReader fr = new FileReader(new File(DESTINATION_FILE_NAME + finalLastIngestedDate));
                                BufferedReader br = new BufferedReader(fr);
                                String line;
                                boolean firstLine = true;
                                List<String> lines = new ArrayList<>();
                                while ((line = br.readLine()) != null) {
                                    if (line.contains("<?xml") && !firstLine) {
                                        // stop
                                        saxParser.parse(new ByteArrayInputStream(String.join("", lines).getBytes()), handler);

                                        if (handler.getPatentNumber() != null) {
                                            System.out.print("Results for "+handler.getPatentNumber()+": ");
                                            int iClaimLength= handler.getIndependentClaimLength();
                                            if(iClaimLength>0) {
                                                System.out.print(iClaimLength+ " ");
                                                patentToIndependentClaimLengthMap.put(handler.getPatentNumber(), iClaimLength);
                                            }
                                            double iClaimRatio = handler.getIndependentClaimRatio();
                                            if(iClaimRatio>0) {
                                                System.out.println(iClaimRatio);
                                                patentToIndependentClaimRatioMap.put(handler.getPatentNumber(), iClaimRatio);
                                            }
                                            double meansPresentRatio = handler.getMeansPresentCountRatio();
                                            System.out.println(meansPresentRatio);
                                            patentToMeansPresentRatioMap.put(handler.getPatentNumber(), meansPresentRatio);
                                            System.out.println();
                                        }

                                        lines.clear();
                                        handler.reset();
                                    }
                                    if (firstLine) firstLine = false;
                                    lines.add(line);
                                }
                                br.close();
                                fr.close();

                                // get the last one
                                if (!lines.isEmpty()) {
                                    // stop
                                    saxParser.parse(new ByteArrayInputStream(String.join("", lines).getBytes()), handler);
                                    lines.clear();
                                    handler.reset();
                                }

                                // Commit results to DB and update last ingest table
                                //Database.updateLastIngestedDate(finalLastIngestedDate);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        } finally {
                            // cleanup
                            // Delete zip and related folders
                            File zipFile = new File(ZIP_FILE_NAME + finalLastIngestedDate);
                            if (zipFile.exists()) zipFile.delete();

                            File xmlFile = new File(DESTINATION_FILE_NAME + finalLastIngestedDate);
                            if (xmlFile.exists()) xmlFile.delete();
                        }

                    }
                };
                action.fork();
                tasks.add(action);

                while(tasks.size()>numTasks) {
                    tasks.remove(0).join();
                }

            }

            while(!tasks.isEmpty()) {
                tasks.remove(0).join();
            }

        } catch(Exception e) {
            e.printStackTrace();
        }


    }



}
