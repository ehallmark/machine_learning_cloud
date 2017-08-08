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
    @Override
    protected void initAndAddFlagsAndEndFlags() {
        boolean debug = true;
        // application flags
        Flag grantNumber = Flag.simpleFlag("doc-number",Constants.NAME, null);
        EndFlag documentFlag = new EndFlag("us-patent-grant") {
            @Override
            public void save() {
                if(debug) {
                    getTransform().forEach((flag, val) -> {
                        String str = val.toString();
                        String cleanVal = str.substring(Math.min(str.length(), 20));
                        if (str.length() > 20) cleanVal += "...";
                        System.out.println(flag.dbName + ": " + cleanVal);
                    });
                }
                dataMap = new HashMap<>();
            }
        };
        endFlags.add(documentFlag);
        grantNumber.setEndFlag(documentFlag);

        Flag publicationReference = Flag.parentFlag("publication-reference");
        documentFlag.addChild(publicationReference);
        publicationReference.addChild(grantNumber);
        publicationReference.addChild(Flag.dateFlag("date",Constants.PUBLICATION_DATE,documentFlag, DateTimeFormatter.BASIC_ISO_DATE).withTransformationFunction(Flag.defaultISODateTransformationFunction));
        publicationReference.addChild(Flag.simpleFlag("country",Constants.COUNTRY,documentFlag));
        publicationReference.addChild(Flag.simpleFlag("kind",Constants.DOC_KIND,documentFlag));

        Flag applicationReference = Flag.parentFlag("application-reference");
        applicationReference.addChild(Flag.dateFlag("date",Constants.FILING_DATE,documentFlag, DateTimeFormatter.BASIC_ISO_DATE).withTransformationFunction(Flag.defaultISODateTransformationFunction));
        applicationReference.addChild(Flag.simpleFlag("country",Constants.FILING_COUNTRY,documentFlag));
        applicationReference.addChild(Flag.simpleFlag("kind",Constants.FILING_DOC_KIND,documentFlag));
        applicationReference.addChild(Flag.simpleFlag("doc-number",Constants.FILING_NAME,documentFlag));

        documentFlag.addChild(Flag.simpleFlag("abstract", Constants.ABSTRACT,documentFlag));
        documentFlag.addChild(Flag.simpleFlag("invention_title",Constants.INVENTION_TITLE,documentFlag));
        documentFlag.addChild(Flag.integerFlag("length-of-grant",Constants.LENGTH_OF_GRANT,documentFlag));

        Flag citedDoc = Flag.simpleFlag("doc-number",Constants.NAME,null);
        EndFlag citationFlag = new EndFlag("us-citation") {
            @Override
            public void save() {
                if(debug) {
                    getTransform().forEach((flag, val) -> {
                        String str = val.toString();
                        String cleanVal = str.substring(Math.min(str.length(), 20));
                        if (str.length() > 20) cleanVal += "...";
                        System.out.println(flag.dbName + ": " + cleanVal);
                    });
                }
            }
        };
        endFlags.add(citationFlag);
        citedDoc.setEndFlag(citationFlag);
        Flag patCit = Flag.parentFlag("patcit");
        patCit.addChild(Flag.simpleFlag("country",Constants.COUNTRY,citationFlag));
        patCit.addChild(Flag.simpleFlag("kind",Constants.DOC_KIND,citationFlag));
        patCit.addChild(Flag.dateFlag("date",Constants.CITED_DATE,citationFlag, DateTimeFormatter.BASIC_ISO_DATE).withTransformationFunction(Flag.defaultISODateTransformationFunction));
        citationFlag.addChild(patCit);
        citationFlag.addChild(Flag.simpleFlag("category",Constants.CITATION_CATEGORY,citationFlag));

    }

    @Override
    public CustomHandler newInstance() {
        return new USPTOHandler();
    }

    @Override
    public void save() {

    }

    public static void main(String[] args) {
        WebIterator iterator = new ZipFileIterator(new File("data/patents"), "temp_dir_test",(a, b)->true);
        iterator.applyHandlers(new USPTOHandler());
    }
}