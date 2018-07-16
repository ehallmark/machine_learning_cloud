package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import com.google.gson.Gson;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import seeding.google.elasticsearch.Attributes;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**

 */
public class USPTOHandler extends NestedHandler {
    private static final AtomicLong cnt = new AtomicLong(0);
    private static final AtomicLong errors = new AtomicLong(0);
    protected final String topLevelTag;
    protected boolean applications;
    private boolean testing;
    public USPTOHandler(String topLevelTag, boolean applications, boolean testing) {
        this.topLevelTag=topLevelTag;
        this.testing=testing;
        this.applications=applications;
    }

    @Override
    protected void initAndAddFlagsAndEndFlags() {
        int batchSize = 5000;
        List<EndFlag> nestedEndFlags = new ArrayList<>();

        // application flags
        EndFlag documentFlag = new EndFlag(topLevelTag) {
            @Override
            public void save() {
                try {
                    //debug(this, debug, attrsToIngest);
                    Map<String, Object> toIngest = getTransform(null);
                    String pub_num = (String)toIngest.get(Attributes.PUBLICATION_NUMBER);
                    String app_num = (String)toIngest.get(Attributes.APPLICATION_NUMBER_FORMATTED);
                    if (pub_num == null){
                        System.out.println("NO NAME!!!!!!!!!!");
                        if(errors.getAndIncrement()%10==0) {
                            System.out.println(errors.get());
                        }
                        return;
                    }
                    String name = toIngest.getOrDefault(Attributes.COUNTRY_CODE, "US")+pub_num+toIngest.get(Attributes.KIND_CODE);
                    toIngest.put(Attributes.PUBLICATION_NUMBER_FULL, name);
                    toIngest.put(Attributes.PUBLICATION_NUMBER_WITH_COUNTRY, toIngest.getOrDefault(Attributes.COUNTRY_CODE, "US")+pub_num);
                    if(app_num!=null) {
                        toIngest.put(Attributes.APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY, toIngest.getOrDefault(Attributes.COUNTRY_CODE, "US") + app_num);
                    }
                    nestedEndFlags.forEach(endFlag -> {
                        List<Map<String, Object>> data = endFlag.dataQueue;
                        if (data.isEmpty() || endFlag.children.isEmpty()) return;
                        if (endFlag.isArray()) {
                            // add as array
                            toIngest.put(endFlag.dbName, data.stream().map(map -> map.values().stream().findAny().orElse(null)).filter(d -> d != null).collect(Collectors.toList()));
                        } else {
                            toIngest.put(endFlag.dbName, data.stream().filter(map -> map.size() > 0).collect(Collectors.toList()));
                        }
                    });
                    synchronized (USPTOHandler.class) {
                        //queue.put(name.toString(), toIngest);
                        if(cnt.getAndIncrement() % batchSize == batchSize-1) {
                            System.out.println(cnt.get());
                        }
                        if(!testing) {
                            saveElasticSearch(name, toIngest);
                        } else {
                            System.out.println("GSON for "+name+": "+new Gson().toJson(toIngest));
                        }
                    }
                     //System.out.println("Ingesting: "+new Gson().toJson(toIngest));
                } finally {
                    // clear dataqueues
                    dataQueue.clear();
                    nestedEndFlags.forEach(endFlag->endFlag.dataQueue.clear());
                }
            }
        };
        endFlags.add(documentFlag);

        Flag publicationReference = Flag.parentFlag("publication-reference");
        documentFlag.addChild(publicationReference);
        publicationReference.addChild(Flag.simpleFlag("doc-number",Attributes.PUBLICATION_NUMBER, documentFlag).withTransformationFunction(Flag.unknownDocumentHandler));
        publicationReference.addChild(Flag.dateFlag("date",Attributes.PUBLICATION_DATE,documentFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));
        publicationReference.addChild(Flag.simpleFlag("country",Attributes.COUNTRY_CODE,documentFlag));
        publicationReference.addChild(Flag.simpleFlag("kind",Attributes.KIND_CODE,documentFlag));

        Flag applicationReference = Flag.parentFlag("application-reference");
        documentFlag.addChild(applicationReference);
        applicationReference.addChild(Flag.dateFlag("date",Attributes.FILING_DATE,documentFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));
        applicationReference.addChild(Flag.simpleFlag("country",Attributes.COUNTRY_CODE,documentFlag));
        applicationReference.addChild(Flag.simpleFlag("doc-number",Attributes.APPLICATION_NUMBER_FORMATTED,documentFlag).withTransformationFunction(Flag.filingDocumentHandler));
        documentFlag.addChild(Flag.simpleFlag("abstract", Attributes.ABSTRACT,documentFlag));
        documentFlag.addChild(Flag.simpleFlag("description", Attributes.DESCRIPTION,documentFlag));
        documentFlag.addChild(Flag.simpleFlag("invention-title",Attributes.INVENTION_TITLE,documentFlag));

        Flag priorityClaims = Flag.parentFlag("priority-claim");
        documentFlag.addChild(priorityClaims);
        priorityClaims.addChild(Flag.dateFlag("date",Attributes.PRIORITY_DATE, documentFlag).withTransformationFunction(f -> val -> {
            Object date = Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE).apply(f).apply(val);
            if(date!=null && f.getEndFlag().getDataMap().containsKey(f)) {
                Object prevDate = Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE).apply(f).apply(f.getEndFlag().getDataMap().get(f));
                if(prevDate!=null) {
                    LocalDate prev = LocalDate.parse((String)prevDate, DateTimeFormatter.ISO_DATE);
                    LocalDate curr = LocalDate.parse((String)date, DateTimeFormatter.ISO_DATE);
                    if(curr.isAfter(prev)) {
                        return prevDate; // want the earliest priority date
                    }
                }
            }
            return date;
        }));

        EndFlag citationFlag = new EndFlag("citation") {
            {
                dbName = Attributes.CITATIONS;
            }
            @Override
            public void save() {
                Map<String, Object> transform = getTransform(null);
                if(transform.containsKey(Attributes.CITED_PUBLICATION_NUMBER)) {
                    transform.put(Attributes.CITED_PUBLICATION_NUMBER_WITH_COUNTRY, (String)transform.get(Attributes.COUNTRY_CODE)+transform.get(Attributes.CITED_PUBLICATION_NUMBER));
                    transform.put(Attributes.CITED_PUBLICATION_NUMBER_FULL, (String)transform.get(Attributes.COUNTRY_CODE)+transform.get(Attributes.CITED_PUBLICATION_NUMBER)+transform.get(Attributes.KIND_CODE));
                }
                if(transform.containsKey(Attributes.CITED_APPLICATION_NUMBER_FORMATTED)) {
                    transform.put(Attributes.CITED_APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY, (String)transform.get(Attributes.COUNTRY_CODE)+transform.get(Attributes.CITED_APPLICATION_NUMBER_FORMATTED));
                }
                if(transform.containsKey(Attributes.CITED_PUBLICATION_NUMBER)) {
                    dataQueue.add(transform);
                }
            }
        };
        citationFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(citationFlag);
        Flag patCit = Flag.parentFlag("patcit");
        patCit.addChild(Flag.simpleFlag("doc-number",Attributes.CITED_PUBLICATION_NUMBER,citationFlag).withTransformationFunction(Flag.unknownDocumentHandler));
        patCit.addChild(Flag.simpleFlag("country",Attributes.COUNTRY_CODE,citationFlag));
        patCit.addChild(Flag.simpleFlag("kind",Attributes.KIND_CODE,citationFlag));
        patCit.addChild(Flag.dateFlag("date",Attributes.CITED_FILING_DATE,citationFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));
        citationFlag.addChild(patCit);
        citationFlag.addChild(Flag.simpleFlag("category",Attributes.CITED_CATEGORY,citationFlag));

        // parties
        EndFlag applicantFlag = new EndFlag("applicant") {
            {
                dbName = Constants.APPLICANTS;
            }
            @Override
            public void save() {
                Map<String,Object> transform = getTransform(null);
                String name =  transform.getOrDefault(Constants.LAST_NAME, "") + " " + transform.getOrDefault(Constants.FIRST_NAME, "");
                name = name.trim().toUpperCase();
                if(name.length() > 0) {
                    transform.put(Attributes.ASSIGNEE_HARMONIZED, name);
                    dataQueue.add(transform);
                }
            }
        };
        applicantFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(applicantFlag);
        applicantFlag.addChild(Flag.simpleFlag("last-name",Constants.LAST_NAME, applicantFlag));
        applicantFlag.addChild(Flag.simpleFlag("first-name",Constants.FIRST_NAME, applicantFlag));
        applicantFlag.addChild(Flag.simpleFlag("country",Attributes.ASSIGNEE_HARMONIZED_CC, applicantFlag));


        EndFlag inventorFlag = new EndFlag("inventor") {
            {
                dbName = Attributes.INVENTORS;
            }
            @Override
            public void save() {
                Map<String,Object> transform = getTransform(null);
                String name =  transform.getOrDefault(Constants.LAST_NAME, "") + " " + transform.getOrDefault(Constants.FIRST_NAME, "");
                name = name.trim().toUpperCase();
                if(name.length()>0) {
                    transform.put(Attributes.INVENTOR_HARMONIZED, name);
                    dataQueue.add(transform);
                }
            }
        };
        inventorFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(inventorFlag);
        inventorFlag.addChild(Flag.simpleFlag("last-name",Constants.LAST_NAME, inventorFlag));
        inventorFlag.addChild(Flag.simpleFlag("first-name",Constants.FIRST_NAME, inventorFlag));
        inventorFlag.addChild(Flag.simpleFlag("country",Attributes.INVENTOR_HARMONIZED_CC, inventorFlag));


        EndFlag assigneeFlag = new EndFlag("assignee") {
            {
                dbName = Attributes.ASSIGNEES;
            }
            @Override
            public void save() {
                Map<String,Object> transform = getTransform(null);
                if(!transform.containsKey(Attributes.ASSIGNEE_HARMONIZED)) {
                    String name = transform.getOrDefault(Constants.LAST_NAME, "") + " " + transform.getOrDefault(Constants.FIRST_NAME, "");
                    name = name.trim().toUpperCase();
                    if(name.length()>0) {
                        transform.put(Attributes.ASSIGNEE_HARMONIZED, name);
                    }
                }
                if(transform.containsKey(Attributes.ASSIGNEE_HARMONIZED)) {
                    dataQueue.add(transform);
                }
            }
        };
        assigneeFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(assigneeFlag);
        assigneeFlag.addChild(Flag.simpleFlag("last-name",Constants.LAST_NAME, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("first-name",Constants.FIRST_NAME, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("country",Attributes.ASSIGNEE_HARMONIZED_CC, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("role",Constants.ASSIGNEE_ROLE, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("orgname", Attributes.ASSIGNEE_HARMONIZED, assigneeFlag).withTransformationFunction(Flag.assigneeTransformationFunction));


        EndFlag claimFlag = new EndFlag("claim") {
            {
                dbName = Attributes.CLAIMS;
            }
            @Override
            public void save() {
                dataQueue.add(getTransform(null));
            }
        };
        endFlags.add(claimFlag);
        claimFlag.addChild(Flag.simpleFlag("claim",Attributes.CLAIMS,claimFlag).withTransformationFunction(Flag.claimTextFunction));
        claimFlag.addChild(Flag.integerFlag("num",Constants.CLAIM_NUM,claimFlag).isAttributesFlag(true).withTransformationFunction(f->s->{
            try {
                return Integer.valueOf(s);
            } catch(Exception nfe) {
                return null;
            }
        }));
        claimFlag.addChild(Flag.integerFlag("claim",Constants.CLAIM_LENGTH,claimFlag).withTransformationFunction(f->s->s==null?0:s.split("\\s+").length));
        claimFlag.addChild(Flag.integerFlag("claim",Attributes.LENGTH_OF_SMALLEST_IND_CLAIM,claimFlag).setIsForeign(true).withTransformationFunction(Flag.smallestIndClaimTransformationFunction(documentFlag)));

        Flag claimRefWrapper = Flag.parentFlag("claim-ref");
        claimFlag.addChild(claimRefWrapper);
        claimRefWrapper.addChild(Flag.integerFlag("idref",Constants.PARENT_CLAIM_NUM,claimFlag).isAttributesFlag(true).withTransformationFunction(f->s->{
            try {
                return Integer.valueOf(s.substring(4));
            } catch(Exception e) {
                return null;
            }
        }));
        // means present data
        claimFlag.addChild(Flag.booleanFlag("claim",Attributes.MEANS_PRESENT,claimFlag).setIsForeign(true).withTransformationFunction(f->s->{
            String parentClaimNum = f.getDataForField(Constants.PARENT_CLAIM_NUM);
            boolean isIndependent = parentClaimNum==null || parentClaimNum.isEmpty();
            if(isIndependent) {
                boolean meansPresent = s!=null && s.contains(" means ");
                String data = documentFlag.getDataMap().get(f);
                if (data == null || data.equals("true")) {
                    // add
                    documentFlag.getDataMap().put(f, String.valueOf(meansPresent));
                }
            }
            return null; // prevent default behavior (must have isForeign set to true)
        }));
        nestedEndFlags.addAll(endFlags.stream().filter(f->!f.equals(documentFlag)).collect(Collectors.toList()));
    }

    @Override
    public CustomHandler newInstance() {
        USPTOHandler handler = new USPTOHandler(topLevelTag, applications, testing);
        handler.init();
        return handler;
    }

    @Override
    public void save() {
    }

    private void saveElasticSearch(String name, Map<String,Object> doc) {
        //System.out.println("Ingesting GSON for " + name + ": " + new Gson().toJson(doc));
        final String sql = "insert into patents_global (publication_number_full,publication_number,application_number_formatted,filing_date,publication_date,priority_date,country_code,kind_code,application_kind,family_id,invention_title,invention_title_lang,abstract,abstract_lang,claims,claims_lang,description,description_lang,inventor,assignee,inventor_harmonized,inventor_harmonized_cc,assignee_harmonized,assignee_harmonized_cc,cited_publication_number_full,cited_application_number_full,cited_npl_text,cited_type,cited_category,cited_filing_date,means_present,length_of_smallest_ind_claim) values " +
                "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::date[],?,?) on conflict do nothing";

        try {
            PreparedStatement ps = Database.getConn().prepareStatement(sql);
            ps.setString(1, name);
            ps.setObject(2, doc.get(Attributes.PUBLICATION_NUMBER));
            ps.setObject(3, doc.get(Attributes.APPLICATION_NUMBER_FORMATTED));
            ps.setObject(4, doc.get(Attributes.FILING_DATE));
            ps.setObject(5, doc.get(Attributes.PUBLICATION_DATE));
            ps.setObject(6, doc.get(Attributes.PRIORITY_DATE));
            ps.setObject(7, doc.get(Attributes.COUNTRY_CODE));
            ps.setObject(8, doc.get(Attributes.KIND_CODE));
            ps.setObject(9, doc.get(Attributes.APPLICATION_KIND));
            ps.setObject(10, doc.get("-1"));
            {
                Object title = doc.get(Attributes.INVENTION_TITLE);
                if (title != null) {
                    title = new String[]{(String) title};
                    ps.setArray(11, Database.getConn().createArrayOf("varchar", (String[]) title));
                    ps.setArray(12, Database.getConn().createArrayOf("varchar", new String[]{"en"}));
                } else {
                    ps.setObject(11, (Object[]) null);
                    ps.setObject(12, (Object[]) null);
                }
            }
            {
                Object abstrac = doc.get(Attributes.ABSTRACT);
                if (abstrac != null) {
                    abstrac = new String[]{(String) abstrac};
                    ps.setArray(13, Database.getConn().createArrayOf("varchar", (String[]) abstrac));
                    ps.setArray(14, Database.getConn().createArrayOf("varchar", new String[]{"en"}));
                } else {
                    ps.setObject(13, (Object[]) null);
                    ps.setObject(14, (Object[]) null);
                }
            }
            {
                Object claims = doc.get(Attributes.CLAIMS);
                if (claims != null) {
                    claims = String.join(" \n \n \n ", ((List<Map<String, Object>>) claims).stream().map(o -> (String) o.getOrDefault(Attributes.CLAIMS, "")).collect(Collectors.toList())).trim();
                    claims = new String[]{(String) claims};
                    ps.setArray(15, Database.getConn().createArrayOf("varchar", (String[]) claims));
                    ps.setArray(16, Database.getConn().createArrayOf("varchar", new String[]{"en"}));
                } else {
                    ps.setObject(14, (Object[]) null);
                    ps.setObject(16, (Object[]) null);
                }
            }
            {
                Object description = doc.get(Attributes.DESCRIPTION);
                if (description != null) {
                    description = new String[]{(String) description};
                    ps.setArray(17, Database.getConn().createArrayOf("varchar", (String[]) description));
                    ps.setArray(18, Database.getConn().createArrayOf("varchar", new String[]{"en"}));
                } else {
                    ps.setObject(17, (Object[]) null);
                    ps.setObject(18, (Object[]) null);
                }
            }
            {
                List<Map<String,Object>>  inventors = (List<Map<String,Object>>) doc.get(Attributes.INVENTORS);
                if(inventors!=null) {
                    Object[] inventor_harmonized = new Object[inventors.size()];
                    Object[] inventor_harmonized_cc = new Object[inventors.size()];
                    for(int i = 0; i < inventors.size(); i++) {
                        Map<String,Object> inventor = inventors.get(i);
                        inventor_harmonized[i] = inventor.get(Attributes.INVENTOR_HARMONIZED);
                        inventor_harmonized_cc[i] = inventor.getOrDefault(Attributes.INVENTOR_HARMONIZED_CC, "US");
                    }

                    ps.setArray(19, Database.getConn().createArrayOf("varchar", inventor_harmonized));
                    ps.setArray(21, Database.getConn().createArrayOf("varchar", inventor_harmonized));
                    ps.setArray(22, Database.getConn().createArrayOf("varchar", inventor_harmonized_cc));

                } else {
                    ps.setObject(19, (Object[]) null);
                    ps.setObject(21, (Object[]) null);
                    ps.setObject(22, (Object[]) null);
                }
            }
            {
                List<Map<String,Object>> assignees = (List<Map<String,Object>>)doc.get(Attributes.ASSIGNEES);
                if(assignees==null) {
                    assignees = (List<Map<String,Object>>)doc.get(Constants.APPLICANTS);
                }
                if(assignees!=null) {
                    Object[] assignee_harmonized = new Object[assignees.size()];
                    Object[] assignee_harmonized_cc = new Object[assignees.size()];
                    for(int i = 0; i < assignees.size(); i++) {
                        Map<String,Object> assignee = assignees.get(i);
                        assignee_harmonized[i] = assignee.get(Attributes.ASSIGNEE_HARMONIZED);
                        assignee_harmonized_cc[i] = assignee.getOrDefault(Attributes.ASSIGNEE_HARMONIZED_CC, "US");
                    }

                    ps.setArray(20, Database.getConn().createArrayOf("varchar", assignee_harmonized));
                    ps.setArray(23, Database.getConn().createArrayOf("varchar", assignee_harmonized));
                    ps.setArray(24, Database.getConn().createArrayOf("varchar", assignee_harmonized_cc));

                } else {
                    ps.setObject(20, (Object[]) null);
                    ps.setObject(23, (Object[]) null);
                    ps.setObject(24, (Object[]) null);
                }
            }
            { //cited_publication_number_full,cited_application_number_full,cited_npl_text,cited_type,cited_category,cited_filing_date
                List<Map<String,Object>> citations = (List<Map<String,Object>>)doc.get(Attributes.CITATIONS);
                if(citations!=null) {
                    Object[] cited_publication_number_full = new Object[citations.size()];
                    Object[] cited_application_number_full = new Object[citations.size()];
                    Object[] cited_npl_text = new Object[citations.size()];
                    Object[] cited_type = new Object[citations.size()];
                    Object[] cited_category = new Object[citations.size()];
                    Object[] cited_filing_date = new Object[citations.size()];

                    for(int i = 0; i < citations.size(); i++) {
                        Map<String,Object> citation = citations.get(i);
                        cited_publication_number_full[i]=citation.get(Attributes.CITED_PUBLICATION_NUMBER_FULL);
                        cited_application_number_full[i]=null;
                        cited_npl_text[i]=null;
                        cited_type[i]=null;
                        cited_category[i]=citation.get(Attributes.CITED_CATEGORY);
                        cited_filing_date[i]=citation.get(Attributes.CITED_FILING_DATE);
                    }

                    ps.setArray(25, Database.getConn().createArrayOf("varchar", cited_publication_number_full));
                    ps.setArray(26, Database.getConn().createArrayOf("varchar", cited_application_number_full));
                    ps.setArray(27, Database.getConn().createArrayOf("varchar", cited_npl_text));
                    ps.setArray(28, Database.getConn().createArrayOf("varchar", cited_type));
                    ps.setArray(29, Database.getConn().createArrayOf("varchar", cited_category));
                    ps.setArray(30, Database.getConn().createArrayOf("varchar", cited_filing_date));

                } else {
                    ps.setObject(25, (Object[]) null);
                    ps.setObject(26, (Object[]) null);
                    ps.setObject(27, (Object[]) null);
                    ps.setObject(28, (Object[]) null);
                    ps.setObject(29, (Object[]) null);
                    ps.setObject(30, (Object[]) null);
                }
            }
            ps.setObject(31, doc.get(Attributes.MEANS_PRESENT));
            ps.setObject(32, doc.get(Attributes.LENGTH_OF_SMALLEST_IND_CLAIM));
            System.out.println("Executing Ps: "+ps.toString());
            ps.executeUpdate();

        } catch(Exception e) {
            e.printStackTrace();
        }
    }


}