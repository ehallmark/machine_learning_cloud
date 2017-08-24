package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import elasticsearch.DataIngester;
import lombok.Setter;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.portfolios.PortfolioList;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**

 */
public class USPTOHandler extends NestedHandler {
    private static final AtomicLong cnt = new AtomicLong(0);
    private static final AtomicLong errors = new AtomicLong(0);
    protected final String topLevelTag;
    @Setter
    protected static Map<String,INDArray> lookupTable;
    @Setter
    protected static Collection<ComputableAttribute> computableAttributes;
    protected boolean applications;
    public USPTOHandler(String topLevelTag, boolean applications) {
        this.topLevelTag=topLevelTag;
        this.applications=applications;
    }

    private static void debug(EndFlag endFlag, boolean debug, Collection<String> onlyAttrs) {
        if(debug) {
            endFlag.getTransform(onlyAttrs).forEach((flag, val) -> {
                String str = val.toString();
                String cleanVal = str.substring(0, Math.min(str.length(), 20));
                if (str.length() > 20) cleanVal += "...";
                System.out.println(flag + ": " + cleanVal);
            });
        }
    }

    @Override
    protected void initAndAddFlagsAndEndFlags() {
        boolean debug = false;
        int batchSize = 5000;
        List<EndFlag> nestedEndFlags = new ArrayList<>();
        Collection<String> attrsToIngest = SimilarPatentServer.getAllStreamingAttributeNames();
        // application flags
        EndFlag documentFlag = new EndFlag(topLevelTag) {
            @Override
            public void save() {
                try {
                    //debug(this, debug, attrsToIngest);
                    Map<String, Object> toIngest = getTransform(attrsToIngest);
                    Object name = toIngest.get(Constants.NAME);
                    if (name == null){
                        System.out.println("NO NAME!!!!!!!!!!");
                        if(errors.getAndIncrement()%10==0) {
                            System.out.println(errors.get());
                        }
                        return;
                    }
                    // add result type
                    if(applications) {
                        toIngest.put(Constants.DOC_TYPE, PortfolioList.Type.applications.toString());
                    } else {
                        toIngest.put(Constants.DOC_TYPE, PortfolioList.Type.patents.toString());
                    }
                    // try to add vector
                    INDArray vec = lookupTable.get(name);
                    if(vec!=null) {
                        toIngest.put("vector_obj", SimilarPatentServer.vectorToElasticSearchObject(vec));
                    }
                    nestedEndFlags.forEach(endFlag -> {
                        List<Map<String, Object>> data = endFlag.dataQueue;
                        if (data.isEmpty() || endFlag.children.isEmpty()) return;
                        if (endFlag.isArray()) {
                            // add as array
                            toIngest.put(endFlag.dbName, data.stream().map(map -> map.values().stream().findAny().orElse(null)).filter(d -> d != null).collect(Collectors.toList()));
                        } else {
                            if(endFlag.dbName.equals(Constants.LATEST_ASSIGNEE)) {
                                Map<String,Object> latestAssigneeData = data.stream().filter(map -> map.size() > 0).findAny().orElse(null);
                                if(latestAssigneeData!=null) {
                                    toIngest.put(endFlag.dbName, latestAssigneeData);
                                }
                            } else {
                                toIngest.put(endFlag.dbName, data.stream().filter(map -> map.size() > 0).collect(Collectors.toList()));
                            }
                        }
                    });
                    // update computable attrs
                    if(computableAttributes!=null) {
                        computableAttributes.forEach(attr -> {
                            if (applications) {
                                attr.handleApplicationData(name.toString(), toIngest);
                            } else {
                                attr.handlePatentData(name.toString(), toIngest);
                            }
                        });
                    }
                    synchronized (USPTOHandler.class) {
                        //queue.put(name.toString(), toIngest);
                        if(cnt.getAndIncrement() % batchSize == batchSize-1) {
                            System.out.println(cnt.get());
                        }
                        saveElasticSearch(name.toString(),toIngest);
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
        publicationReference.addChild(Flag.simpleFlag("doc-number",Constants.NAME, documentFlag).withTransformationFunction(Flag.unknownDocumentHandler));
        publicationReference.addChild(Flag.dateFlag("date",Constants.PUBLICATION_DATE,documentFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));
        publicationReference.addChild(Flag.simpleFlag("country",Constants.COUNTRY,documentFlag));
        publicationReference.addChild(Flag.simpleFlag("kind",Constants.DOC_KIND,documentFlag));

        Flag applicationReference = Flag.parentFlag("application-reference");
        documentFlag.addChild(applicationReference);
        applicationReference.addChild(Flag.dateFlag("date",Constants.FILING_DATE,documentFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));
        applicationReference.addChild(Flag.simpleFlag("country",Constants.FILING_COUNTRY,documentFlag));
        applicationReference.addChild(Flag.simpleFlag("doc-number",Constants.FILING_NAME,documentFlag).withTransformationFunction(Flag.filingDocumentHandler));
        documentFlag.addChild(Flag.simpleFlag("abstract", Constants.ABSTRACT,documentFlag));
        documentFlag.addChild(Flag.simpleFlag("invention-title",Constants.INVENTION_TITLE,documentFlag));
        documentFlag.addChild(Flag.integerFlag("length-of-grant",Constants.LENGTH_OF_GRANT,documentFlag));
        documentFlag.addChild(Flag.simpleFlag("us-claim-statement",Constants.CLAIM_STATEMENT,documentFlag));

        Flag priorityClaims = Flag.parentFlag("priority-claim");
        documentFlag.addChild(priorityClaims);
        priorityClaims.addChild(Flag.dateFlag("date",Constants.PRIORITY_DATE, documentFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));


        EndFlag claimTextFlag = new EndFlag("claim") {
            {
                isArray=true;
                dbName = Constants.CLAIM;
            }
            @Override
            public void save() {
                dataQueue.add(getTransform(attrsToIngest));
            }
        };
        endFlags.add(claimTextFlag);
        claimTextFlag.addChild(Flag.simpleFlag("claim",Constants.CLAIM,claimTextFlag));

        EndFlag citationFlag = new EndFlag("citation") {
            {
                dbName = Constants.CITATIONS;
            }
            @Override
            public void save() {
                dataQueue.add(getTransform(attrsToIngest));
            }
        };
        citationFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(citationFlag);
        Flag patCit = Flag.parentFlag("patcit");
        patCit.addChild(Flag.simpleFlag("doc-number",Constants.NAME,citationFlag).withTransformationFunction(Flag.unknownDocumentHandler));
        patCit.addChild(Flag.simpleFlag("country",Constants.COUNTRY,citationFlag));
        patCit.addChild(Flag.simpleFlag("kind",Constants.DOC_KIND,citationFlag));
        patCit.addChild(Flag.dateFlag("date",Constants.CITED_DATE,citationFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));
        citationFlag.addChild(patCit);
        citationFlag.addChild(Flag.simpleFlag("category",Constants.CITATION_CATEGORY,citationFlag));

        // parties
        EndFlag applicantFlag = new EndFlag("applicant") {
            {
                dbName = Constants.APPLICANTS;
            }
            @Override
            public void save() {
                dataQueue.add(getTransform(attrsToIngest));
            }
        };
        applicantFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(applicantFlag);
        applicantFlag.addChild(Flag.simpleFlag("last-name",Constants.LAST_NAME, applicantFlag));
        applicantFlag.addChild(Flag.simpleFlag("first-name",Constants.FIRST_NAME, applicantFlag));
        applicantFlag.addChild(Flag.simpleFlag("city",Constants.CITY, applicantFlag));
        applicantFlag.addChild(Flag.simpleFlag("state",Constants.STATE, applicantFlag));
        applicantFlag.addChild(Flag.simpleFlag("country",Constants.COUNTRY, applicantFlag));


        EndFlag inventorFlag = new EndFlag("inventor") {
            {
                dbName = Constants.INVENTORS;
            }
            @Override
            public void save() {
                dataQueue.add(getTransform(attrsToIngest));
            }
        };
        inventorFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(inventorFlag);
        inventorFlag.addChild(Flag.simpleFlag("last-name",Constants.LAST_NAME, inventorFlag));
        inventorFlag.addChild(Flag.simpleFlag("first-name",Constants.FIRST_NAME, inventorFlag));
        inventorFlag.addChild(Flag.simpleFlag("city",Constants.CITY, inventorFlag));
        inventorFlag.addChild(Flag.simpleFlag("state",Constants.STATE, inventorFlag));
        inventorFlag.addChild(Flag.simpleFlag("country",Constants.COUNTRY, inventorFlag));


        EndFlag agentFlag = new EndFlag("agent") {
            {
                dbName = Constants.AGENTS;
            }
            @Override
            public void save() {
                dataQueue.add(getTransform(attrsToIngest));
            }
        };
        agentFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(agentFlag);
        agentFlag.addChild(Flag.simpleFlag("last-name",Constants.LAST_NAME, agentFlag));
        agentFlag.addChild(Flag.simpleFlag("first-name",Constants.FIRST_NAME, agentFlag));
        agentFlag.addChild(Flag.simpleFlag("city",Constants.CITY, agentFlag));
        agentFlag.addChild(Flag.simpleFlag("state",Constants.STATE, agentFlag));
        agentFlag.addChild(Flag.simpleFlag("country",Constants.COUNTRY, agentFlag));


        EndFlag assigneeFlag = new EndFlag("assignee") {
            {
                dbName = Constants.ASSIGNEES;
            }
            @Override
            public void save() {
                //debug(this,debug,Arrays.asList(Constants.JAPANESE_ASSIGNEE, Constants.ASSIGNEE, Constants.LATEST_ASSIGNEE));
                dataQueue.add(getTransform(attrsToIngest));
            }
        };
        assigneeFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(assigneeFlag);
        assigneeFlag.addChild(Flag.simpleFlag("last-name",Constants.LAST_NAME, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("first-name",Constants.FIRST_NAME, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("city",Constants.CITY, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("state",Constants.STATE, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("country",Constants.COUNTRY, assigneeFlag));
        assigneeFlag.addChild(Flag.booleanFlag("country",Constants.JAPANESE_ASSIGNEE, assigneeFlag).withTransformationFunction(f->s->s.equals("JP")||s.equals("Japan")||s.equals("JPX")));
        assigneeFlag.addChild(Flag.simpleFlag("role",Constants.ASSIGNEE_ROLE, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("orgname", Constants.ASSIGNEE, assigneeFlag).withTransformationFunction(Flag.assigneeTransformationFunction));

        EndFlag latestAssignee = new EndFlag("assignee") {
            {
                dbName = Constants.LATEST_ASSIGNEE;
            }
            @Override
            public void save() {
                //debug(this,debug,Arrays.asList(Constants.JAPANESE_ASSIGNEE, Constants.ASSIGNEE, Constants.LATEST_ASSIGNEE));
                if(dataQueue.isEmpty()) dataQueue.add(getTransform(attrsToIngest));
            }
        };
        latestAssignee.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(latestAssignee);
        latestAssignee.addChild(Flag.simpleFlag("last-name",Constants.LAST_NAME, latestAssignee));
        latestAssignee.addChild(Flag.simpleFlag("first-name",Constants.FIRST_NAME, latestAssignee));
        latestAssignee.addChild(Flag.simpleFlag("city",Constants.CITY, latestAssignee));
        latestAssignee.addChild(Flag.simpleFlag("state",Constants.STATE, latestAssignee));
        latestAssignee.addChild(Flag.simpleFlag("country",Constants.COUNTRY, latestAssignee));
        latestAssignee.addChild(Flag.booleanFlag("country",Constants.JAPANESE_ASSIGNEE, latestAssignee).withTransformationFunction(f->s->s.equals("JP")||s.equals("Japan")||s.equals("JPX")));
        latestAssignee.addChild(Flag.simpleFlag("role",Constants.ASSIGNEE_ROLE, latestAssignee));
        latestAssignee.addChild(Flag.simpleFlag("orgname", Constants.ASSIGNEE, latestAssignee).withTransformationFunction(Flag.assigneeTransformationFunction));


        EndFlag claimFlag = new EndFlag("claim") {
            {
                dbName = Constants.CLAIMS;
            }
            @Override
            public void save() {
                //debug(this,debug, Arrays.asList(Constants.MEANS_PRESENT,Constants.CLAIM_LENGTH,Constants.PARENT_CLAIM_NUM,Constants.SMALLEST_INDEPENDENT_CLAIM_LENGTH));
                dataQueue.add(getTransform(attrsToIngest));
            }
        };
        endFlags.add(claimFlag);
        claimFlag.addChild(Flag.simpleFlag("claim",Constants.CLAIM,claimFlag));
        claimFlag.addChild(Flag.integerFlag("num",Constants.CLAIM_NUM,claimFlag).isAttributesFlag(true).withTransformationFunction(f->s->{
            try {
                return Integer.valueOf(s);
            } catch(Exception nfe) {
                return null;
            }
        }));
        claimFlag.addChild(Flag.integerFlag("claim",Constants.CLAIM_LENGTH,claimFlag).withTransformationFunction(f->s->s==null?0:s.split("\\s+").length));
        claimFlag.addChild(Flag.integerFlag("claim",Constants.SMALLEST_INDEPENDENT_CLAIM_LENGTH,claimFlag).setIsForeign(true).withTransformationFunction(f->
                s->{
                    if (s == null) s = "";
                    String parentClaimNum = f.getDataForField(Constants.PARENT_CLAIM_NUM);
                    boolean isIndependent = (parentClaimNum==null || parentClaimNum.isEmpty()) && !s.contains("(canceled)");
                    if(isIndependent) {
                        String previousWordCount = documentFlag.getDataMap().get(f);
                        int wordCount = s.split("\\s+").length;
                        if (previousWordCount == null || previousWordCount.isEmpty() || Integer.valueOf(previousWordCount) > wordCount) {
                            documentFlag.getDataMap().put(f, String.valueOf(wordCount));
                        }
                    }
                    return null; // prevent default behavior (must have isForeign set to true)
                })
        );
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
        claimFlag.addChild(Flag.booleanFlag("claim",Constants.MEANS_PRESENT,claimFlag).setIsForeign(true).withTransformationFunction(f->s->{
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

        // related assets data
        EndFlag relatedAssetsFlag = new EndFlag(Constants.RELATED_DOC_TYPE_LIST) {
            {
                dbName=Constants.PATENT_FAMILY;
            }
            @Override
            public void save() {
                Map<String,Object> resultMap = getTransform(attrsToIngest);
                if(currentTag != null) {
                    resultMap.put(Constants.RELATION_TYPE, currentTag);
                }
                dataQueue.add(resultMap);
            }
        };
        endFlags.add(relatedAssetsFlag);
        Flag docFlag = Flag.parentFlag("document-id");
        docFlag.addChild(Flag.simpleFlag("country",Constants.COUNTRY,relatedAssetsFlag));
        docFlag.addChild(Flag.simpleFlag("kind",Constants.DOC_KIND,relatedAssetsFlag));
        docFlag.addChild(Flag.simpleFlag("doc-number",Constants.NAME,relatedAssetsFlag).withTransformationFunction(Flag.unknownDocumentHandler));
        relatedAssetsFlag.addChild(docFlag);

        nestedEndFlags.addAll(endFlags.stream().filter(f->!f.equals(documentFlag)).collect(Collectors.toList()));

    }

    @Override
    public CustomHandler newInstance() {
        USPTOHandler handler = new USPTOHandler(topLevelTag, applications);
        handler.init();
        return handler;
    }

    @Override
    public void save() {
        if (computableAttributes != null) {
            computableAttributes.forEach(attr -> {
                attr.save();
            });
        }
    }

    private void saveElasticSearch(String name, Map<String,Object> doc) {
        Object filingName = doc.get(Constants.FILING_NAME);
        if(filingName != null) {
            DataIngester.ingestBulk(name, filingName.toString(), doc, true);
        }
    }


}