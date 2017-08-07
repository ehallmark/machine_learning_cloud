package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import seeding.ai_db_updater.iterators.FileIterator;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;

import java.io.File;
import java.util.HashMap;

/**

 */
public class USPTOHandler extends NestedHandler {
    @Override
    protected void initAndAddFlagsAndEndFlags() {
        // application flags
        Flag grantNumber = Flag.simpleFlag("doc-number","pub_doc_number", null);
        EndFlag documentFlag = new EndFlag("us-patent-grant") {
            @Override
            public void save() {
                dataMap.forEach((flag,val)->{
                    System.out.println(flag.dbName+": "+val);
                });
                dataMap = new HashMap<>();
            }
        };
        endFlags.add(documentFlag);

        grantNumber.setEndFlag(documentFlag);

        Flag publicationReference = Flag.parentFlag("publication-reference", documentFlag);
        documentFlag.addChild(publicationReference);
        publicationReference.addChild(grantNumber);
        documentFlag.addChild(Flag.simpleFlag("abstract","abstract",documentFlag));

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