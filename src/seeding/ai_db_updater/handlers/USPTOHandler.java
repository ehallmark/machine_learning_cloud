package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import seeding.Constants;
import seeding.ai_db_updater.iterators.FileIterator;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

/**

 */
public class USPTOHandler extends NestedHandler {
    protected final String topLevelTag;
    public USPTOHandler(boolean applications) {
        //if(applications) {
        //    topLevelTag = "us-patent-application";
        //} else {
            topLevelTag = "us-patent-grant";
        //}
    }

    protected USPTOHandler(String topLevelTag) {
        this.topLevelTag=topLevelTag;
    }

    private static void debug(EndFlag endFlag, boolean debug) {
        if(debug) {
            endFlag.getTransform().forEach((flag, val) -> {
                String str = val.toString();
                String cleanVal = str.substring(0,Math.min(str.length(), 20));
                if (str.length() > 20) cleanVal += "...";
                System.out.println(flag.dbName + ": " + cleanVal);
            });
        }
    }

    @Override
    protected void initAndAddFlagsAndEndFlags() {
        boolean debug = true;
        // application flags
        EndFlag documentFlag = new EndFlag(topLevelTag) {
            @Override
            public void save() {
                debug(this,debug);
            }
        };
        endFlags.add(documentFlag);

        Flag publicationReference = Flag.parentFlag("publication-reference");
        documentFlag.addChild(publicationReference);
        publicationReference.addChild(Flag.simpleFlag("doc-number",Constants.NAME, documentFlag).withTransformationFunction(Flag.unknownDocumentHandler));
        publicationReference.addChild(Flag.dateFlag("date",Constants.PUBLICATION_DATE,documentFlag, DateTimeFormatter.BASIC_ISO_DATE).withTransformationFunction(Flag.defaultISODateTransformationFunction));
        publicationReference.addChild(Flag.simpleFlag("country",Constants.COUNTRY,documentFlag));
        publicationReference.addChild(Flag.simpleFlag("kind",Constants.DOC_KIND,documentFlag));

        Flag applicationReference = Flag.parentFlag("application-reference");
        documentFlag.addChild(applicationReference);
        applicationReference.addChild(Flag.dateFlag("date",Constants.FILING_DATE,documentFlag, DateTimeFormatter.BASIC_ISO_DATE).withTransformationFunction(Flag.defaultISODateTransformationFunction));
        applicationReference.addChild(Flag.simpleFlag("country",Constants.FILING_COUNTRY,documentFlag));
        applicationReference.addChild(Flag.simpleFlag("doc-number",Constants.FILING_NAME,documentFlag).withTransformationFunction(Flag.filingDocumentHandler));
        documentFlag.addChild(Flag.simpleFlag("abstract", Constants.ABSTRACT,documentFlag));
        documentFlag.addChild(Flag.simpleFlag("invention-title",Constants.INVENTION_TITLE,documentFlag));
        documentFlag.addChild(Flag.integerFlag("length-of-grant",Constants.LENGTH_OF_GRANT,documentFlag));
        documentFlag.addChild(Flag.simpleFlag("us-claim-statement",Constants.CLAIM_STATEMENT,documentFlag));

        EndFlag citationFlag = new EndFlag("citation") {
            @Override
            public void save() {
                debug(this,debug);
            }
        };
        citationFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(citationFlag);
        Flag patCit = Flag.parentFlag("patcit");
        patCit.addChild(Flag.simpleFlag("doc-number",Constants.NAME,citationFlag).withTransformationFunction(Flag.unknownDocumentHandler));
        patCit.addChild(Flag.simpleFlag("country",Constants.COUNTRY,citationFlag));
        patCit.addChild(Flag.simpleFlag("kind",Constants.DOC_KIND,citationFlag));
        patCit.addChild(Flag.dateFlag("date",Constants.CITED_DATE,citationFlag, DateTimeFormatter.BASIC_ISO_DATE).withTransformationFunction(Flag.defaultISODateTransformationFunction));
        citationFlag.addChild(patCit);
        citationFlag.addChild(Flag.simpleFlag("category",Constants.CITATION_CATEGORY,citationFlag));

        // parties
        EndFlag applicantFlag = new EndFlag("applicant") {
            @Override
            public void save() {
            }
        };
        applicantFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(applicantFlag);
        applicantFlag.addChild(Flag.simpleFlag("last-name",Constants.APPLICANT_LAST_NAME, applicantFlag));
        applicantFlag.addChild(Flag.simpleFlag("first-name",Constants.APPLICANT_FIRST_NAME, applicantFlag));
        applicantFlag.addChild(Flag.simpleFlag("city",Constants.APPLICANT_CITY, applicantFlag));
        applicantFlag.addChild(Flag.simpleFlag("state",Constants.APPLICANT_STATE, applicantFlag));
        applicantFlag.addChild(Flag.simpleFlag("country",Constants.APPLICANT_COUNTRY, applicantFlag));


        EndFlag inventorFlag = new EndFlag("inventor") {
            @Override
            public void save() {
            }
        };
        inventorFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(inventorFlag);
        inventorFlag.addChild(Flag.simpleFlag("last-name",Constants.INVENTOR_LAST_NAME, inventorFlag));
        inventorFlag.addChild(Flag.simpleFlag("first-name",Constants.INVENTOR_FIRST_NAME, inventorFlag));
        inventorFlag.addChild(Flag.simpleFlag("city",Constants.INVENTOR_CITY, inventorFlag));
        inventorFlag.addChild(Flag.simpleFlag("state",Constants.INVENTOR_STATE, inventorFlag));
        inventorFlag.addChild(Flag.simpleFlag("country",Constants.INVENTOR_COUNTRY, inventorFlag));


        EndFlag agentFlag = new EndFlag("agent") {
            @Override
            public void save() {

            }
        };
        agentFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(agentFlag);
        agentFlag.addChild(Flag.simpleFlag("last-name",Constants.AGENT_LAST_NAME, agentFlag));
        agentFlag.addChild(Flag.simpleFlag("first-name",Constants.AGENT_FIRST_NAME, agentFlag));
        agentFlag.addChild(Flag.simpleFlag("city",Constants.AGENT_CITY, agentFlag));
        agentFlag.addChild(Flag.simpleFlag("state",Constants.AGENT_STATE, agentFlag));
        agentFlag.addChild(Flag.simpleFlag("country",Constants.AGENT_COUNTRY, agentFlag));


        EndFlag assigneeFlag = new EndFlag("assignee") {
            @Override
            public void save() {
                debug(this,debug);
            }
        };
        assigneeFlag.compareFunction = Flag.endsWithCompareFunction;
        endFlags.add(assigneeFlag);
        assigneeFlag.addChild(Flag.simpleFlag("last-name",Constants.ASSIGNEE_LAST_NAME, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("first-name",Constants.ASSIGNEE_FIRST_NAME, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("city",Constants.ASSIGNEE_CITY, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("state",Constants.ASSIGNEE_STATE, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("country",Constants.ASSIGNEE_COUNTRY, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("role",Constants.ASSIGNEE_ROLE, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("orgname", Constants.ASSIGNEE, assigneeFlag).withTransformationFunction(Flag.assigneeTransformationFunction));

        EndFlag claimFlag = new EndFlag("claim") {
            @Override
            public void save() {
                debug(this,debug);
            }
        };
        endFlags.add(claimFlag);
        Flag claim = Flag.simpleFlag("claim",Constants.CLAIM,claimFlag);
        claimFlag.addChild(claim);
        claimFlag.addChild(Flag.integerFlag("num",Constants.CLAIM_NUM,claimFlag).isAttributesFlag(true).withTransformationFunction(f->s->{
            try {
                return Integer.valueOf(s);
            } catch(Exception nfe) {
                return null;
            }
        }));
        Flag claimRefWrapper = Flag.parentFlag("claim-ref");
        claimFlag.addChild(claimRefWrapper);
        claimRefWrapper.addChild(Flag.integerFlag("idref",Constants.PARENT_CLAIM_NUM,claimFlag).isAttributesFlag(true).withTransformationFunction(f->s->{
            try {
                return Integer.valueOf(s.substring(4));
            } catch(Exception e) {
                return null;
            }
        }));
    }

    @Override
    public CustomHandler newInstance() {
        return new USPTOHandler(topLevelTag);
    }

    @Override
    public void save() {

    }

    public static void main(String[] args) {
        boolean seedApplications = true;
        WebIterator iterator = new ZipFileIterator(new File(seedApplications ? "data/applications" : "data/patents"), "temp_dir_test",(a, b)->true);
        iterator.applyHandlers(new USPTOHandler(seedApplications));
    }
}