package seeding.google.postgres.xml;

/**
 * Created by ehallmark on 1/3/17.
 */

import seeding.Constants;
import seeding.ai_db_updater.handlers.CustomHandler;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**

 */
public class PTABHandler extends NestedHandler {
    private static final AtomicLong cnt = new AtomicLong(0);
    private static final AtomicLong errors = new AtomicLong(0);

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
        int batchSize = 10000;
        // application flags
        EndFlag documentFlag = new EndFlag("DATA_RECORD") {
            @Override
            public void save() {
                try {
                    debug(this, debug, null);
                    Map<String,Object> assignmentMap = getTransform(null);
                    synchronized (PTABHandler.class) {
                        if(cnt.getAndIncrement() % batchSize == batchSize-1) {
                            System.out.println(cnt.get());
                        }
                        // for each patent or application
                        ingestFunction.accept(assignmentMap);
                    }
                     //System.out.println("Ingesting: "+new Gson().toJson(toIngest));
                } finally {
                    // clear dataqueues
                    dataQueue.clear();
                }
            }
        };
        endFlags.add(documentFlag);

        documentFlag.addChild(Flag.simpleFlag("APPEAL_NO", "appeal_no", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("INTERFERENCE_NO", "interference_no", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("PATENT_NO", "patent_no", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("PRE_GRANT_PUBLICATION_NO", "pre_grant_publication_no", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("BD_PATENT_APPLICATION_NO", "application_no", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("MAILED_DATE", "mailed_date", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("INVENTOR_FAMILY_NM", "inventor_last_name", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("INVENTOR_GIVEN_NM", "inventor_first_name", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("INVENTOR_STRING_TX", "case_name", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("LAST_MODIFIED_TS", "last_modified", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("FK_DT_DOCUMENT_TYPE_NM", "doc_type", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("STATUS_CD", "status", documentFlag));
        documentFlag.addChild(Flag.simpleFlag("DOCUMENT_IMAGE_ID", "image_id", documentFlag));

    }

    @Override
    public CustomHandler newInstance() {
        PTABHandler handler = new PTABHandler(ingestFunction);
        handler.init();
        return handler;
    }

    private Consumer<Map<String,Object>> ingestFunction;
    public PTABHandler(Consumer<Map<String,Object>> ingestFunction) {
        this.ingestFunction=ingestFunction;
    }

    @Override
    public void save() {

    }

}