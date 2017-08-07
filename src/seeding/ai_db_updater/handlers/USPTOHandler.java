package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import org.xml.sax.SAXException;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import seeding.ai_db_updater.iterators.PatentGrantIterator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
                System.out.println(dataMap.get(grantNumber));
                dataMap = new HashMap<>();
            }
        };
        endFlags.add(documentFlag);

        grantNumber.setEndFlag(documentFlag);

        Flag publicationReference = Flag.parentFlag("publication-reference", documentFlag);
        documentFlag.addChild(publicationReference);
        publicationReference.addChild(grantNumber);
    }

    @Override
    public CustomHandler newInstance() {
        return new USPTOHandler();
    }

    @Override
    public void save() {

    }

    public static void main(String[] args) {
        PatentGrantIterator iterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        iterator.applyHandlers(new USPTOHandler());
    }
}