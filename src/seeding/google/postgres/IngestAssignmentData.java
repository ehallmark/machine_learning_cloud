package seeding.google.postgres;

import models.assignee.normalization.name_correction.NormalizeAssignees;
import seeding.Database;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.AssignmentDataDownloader;
import seeding.data_downloader.FileStreamDataDownloader;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;
import seeding.google.postgres.xml.AssignmentHandler;

import java.io.File;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;


/**
 * Created by Evan on 1/22/2017.
 */
public class IngestAssignmentData {

    private static void ingestData()throws Exception {
        final Connection conn = Database.getConn();

        final String[] assignmentFields = new String[]{
                SeedingConstants.REEL_FRAME,
                SeedingConstants.CONVEYANCE_TEXT,
                SeedingConstants.RECORDED_DATE,
                SeedingConstants.EXECUTION_DATE,
                SeedingConstants.ASSIGNEE+"."+SeedingConstants.NAME,
                SeedingConstants.ASSIGNOR+"."+SeedingConstants.NAME,

        };

        final String[] documentIdFields = new String[]{
                SeedingConstants.REEL_FRAME,
                SeedingConstants.APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY,
                SeedingConstants.DATE
        };

        Set<String> arrayFields = new HashSet<>();
        arrayFields.add(SeedingConstants.ASSIGNEE);
        arrayFields.add(SeedingConstants.ASSIGNOR);
        Set<String> booleanFields = new HashSet<>();

        final String valueStrAssignments = Util.getValueStrFor(assignmentFields,arrayFields,booleanFields);
        final String valueStrDocumentId = Util.getValueStrFor(documentIdFields,arrayFields,booleanFields);

        final String assignments = "insert into big_query_assignments (reel_frame,conveyance_text,recorded_date,execution_date,assignee,assignor) values "+valueStrAssignments+" on conflict do nothing";
        final String assignmentDocumentId = "insert into big_query_assignment_documentid (reel_frame,application_number_formatted_with_country,date) values "+valueStrDocumentId+" on conflict do nothing";

        DefaultApplier applierAssignments = new DefaultApplier(false, conn, assignmentFields);
        QueryStream<List<Object>> queryStreamAssignments = new QueryStream<>(assignments,conn,applierAssignments);

        DefaultApplier applierDocumentId = new DefaultApplier(false, conn, documentIdFields);
        QueryStream<List<Object>> queryStreamDocumentId = new QueryStream<>(assignmentDocumentId,conn,applierDocumentId);

        Consumer<Map<String,Object>> ingest = map -> {
            // handle assignments
            Object reelFrame = map.get(seeding.Constants.REEL_FRAME);
            Object conveyanceText = map.get(seeding.Constants.CONVEYANCE_TEXT);
            Object recordedDate = map.get(seeding.Constants.RECORDED_DATE);
            if(reelFrame==null||recordedDate==null) {
                return;
            }

            List<Map<String,Object>> assignees = (List<Map<String,Object>>)map.get(seeding.Constants.LATEST_ASSIGNEE);
            List<Map<String,Object>> assignors = (List<Map<String,Object>>)map.get(seeding.Constants.ASSIGNORS);
            if(assignees==null||assignees.size()==0||assignors==null||assignors.size()==0) {
                return;
            }

            // check for existence of document ids
            List<Map<String,Object>> records = (List<Map<String,Object>>) map.get(seeding.Constants.NAME);
            if(records==null||records.size()==0) {
                System.out.println("No records...");
                return;
            }

            List<Object> assignmentData = new ArrayList<>(assignmentFields.length);
            assignmentData.add(reelFrame);
            assignmentData.add(conveyanceText);
            assignmentData.add(recordedDate);

            // execution date
            LocalDate minExecutionDate = assignors.stream().map(m->{
                if(m.containsKey(seeding.Constants.EXECUTION_DATE)) {
                    return LocalDate.parse((String)m.get(seeding.Constants.EXECUTION_DATE),DateTimeFormatter.ISO_DATE);
                }
                return null;
            }).filter(l->l!=null).sorted((l1,l2)->l2.compareTo(l1)).limit(1).findFirst().orElse(null);
            if(minExecutionDate==null) {
                assignmentData.add(null);
            } else {
                assignmentData.add(minExecutionDate.format(DateTimeFormatter.ISO_DATE));
            }

            // assignees
            String[] assigneeArray = new String[assignees.size()];
            for(int i = 0; i < assignees.size(); i++) {
                assigneeArray[i] = NormalizeAssignees.manualCleanse((String)assignees.get(i).get(seeding.Constants.ASSIGNEE));
            }
            assignmentData.add(assigneeArray);
            // assignors
            String[] assignorArray = new String[assignors.size()];
            for(int i = 0; i < assignors.size(); i++) {
                assignorArray[i] = NormalizeAssignees.manualCleanse((String)assignors.get(i).get(seeding.Constants.FULL_NAME));
            }
            assignmentData.add(assignorArray);
            try {
                queryStreamAssignments.ingest(assignmentData);
            } catch(Exception e) {
                e.printStackTrace();
                System.out.println("During assignment data");
                System.exit(1);
            }

            for(Map<String,Object> record : records) {
                List<Object> documentData = new ArrayList<>();
                Object docNumber = record.get(seeding.Constants.NAME);
                Object docKind = record.get(seeding.Constants.DOC_KIND);
                Object date = record.get(seeding.Constants.RECORDED_DATE);
                if(docNumber==null||docKind==null) {
                    continue;
                }
                boolean isFiling = docKind.equals("X0");
                if(!isFiling) continue;

                documentData.add(reelFrame);
                documentData.add(docNumber);
                documentData.add(date);
                try {
                    queryStreamDocumentId.ingest(documentData);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("During document id data");
                    System.exit(1);
                }

            }

        };

        // download most recent files and ingest
        FileStreamDataDownloader downloader = new AssignmentDataDownloader("uspto_assignments");
        LocalDate latestDateToStartFrom = LocalDate.of(2021,1,1);
        Function<File,Boolean> orFunction = file -> {
            String name = file.getName();
            try {
                LocalDate fileDate = LocalDate.parse(name, DateTimeFormatter.BASIC_ISO_DATE);
                if(latestDateToStartFrom.isBefore(fileDate)) return true;
            } catch(Exception e) {
            }
            return false;
        };
        WebIterator iterator = new ZipFileIterator(downloader, "assignments_temp", false, false, orFunction, false);
        NestedHandler handler = new AssignmentHandler(ingest);
        handler.init();
        iterator.applyHandlers(handler);
        queryStreamAssignments.close();
        queryStreamDocumentId.close();
    }

    public static void main(String[] args) throws Exception {
        ingestData();
    }
}
