package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import elasticsearch.DataIngester;
import seeding.Constants;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import user_interface.server.SimilarPatentServer;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**

 */
public class USPTOAssignmentHandler extends NestedHandler {
    private static final AtomicLong cnt = new AtomicLong(0);
    private static final AtomicLong errors = new AtomicLong(0);

    private static Map<String,Map<String,Object>> queue = Collections.synchronizedMap(new HashMap<>(5000));

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
        EndFlag documentFlag = new EndFlag("patent-assignment") {
            @Override
            public void save() {
                try {
                    //debug(this, debug, attrsToIngest);
                    Map<String, Object> toIngest = getTransform(attrsToIngest);
                    Object reel = toIngest.get(Constants.REEL_NO);
                    Object frame = toIngest.get(Constants.FRAME_NO);
                    // get reel frame
                    if (reel == null || frame == null){
                        System.out.println("NO NAME!!!!!!!!!!");
                        if(errors.getAndIncrement()%10==0) {
                            System.out.println(errors.get());
                        }
                        return;
                    }
                    String reelFrame = reel.toString()+"/"+frame.toString();
                    toIngest.put(Constants.REEL_FRAME, reelFrame);
                    nestedEndFlags.forEach(endFlag -> {
                        List<Map<String, Object>> data = endFlag.dataQueue;
                        if (data.isEmpty() || endFlag.children.isEmpty()) return;
                        if (endFlag.isArray()) {
                            // add as array
                            toIngest.put(endFlag.dbName, data.stream().map(map -> map.values().stream().findAny().orElse(null)).filter(d -> d != null).collect(Collectors.toList()));
                        } else {
                            toIngest.put(endFlag.dbName, data.stream().filter(map->map.size()>0).collect(Collectors.toList()));
                        }
                    });
                    synchronized (USPTOAssignmentHandler.class) {
                        queue.put(reelFrame, toIngest);
                        if (queue.size() > batchSize) {
                            System.out.println(cnt.getAndAdd(queue.size()));
                            DataIngester.ingestAssets(queue, false);
                            queue.clear();
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

        documentFlag.addChild(Flag.dateFlag("recorded-date", Constants.RECORDED_DATE, documentFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));
        documentFlag.addChild(Flag.simpleFlag("reel-no", Constants.REEL_NO, documentFlag));
        documentFlag.addChild(Flag.simpleFlag("frame-no", Constants.FRAME_NO, documentFlag));
        documentFlag.addChild(Flag.simpleFlag("purge-indicator", Constants.PURGE_INDICATOR, documentFlag));
        documentFlag.addChild(Flag.simpleFlag("conveyance-text", Constants.CONVEYANCE_TEXT, documentFlag));

        Flag correspondFlag = Flag.simpleFlag("correspondent", Constants.CORRESPONDENT, documentFlag);
        documentFlag.addChild(correspondFlag);
        correspondFlag.addChild(Flag.simpleFlag("name", Constants.FULL_NAME, documentFlag));

        EndFlag assignorFlag = new EndFlag("patent-assignor") {
            {
               // dbName=Constants.ASSIGNORS;
            }
            @Override
            public void save() {

            }
        };
        endFlags.add(assignorFlag);

        assignorFlag.addChild(Flag.simpleFlag("name",Constants.FULL_NAME, assignorFlag));
        assignorFlag.addChild(Flag.dateFlag("execution-date", Constants.EXECUTION_DATE, assignorFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));

        //assignorFlag.addChild(Flag.dateFlag("date",));

        nestedEndFlags.addAll(endFlags.stream().filter(f->!f.equals(documentFlag)).collect(Collectors.toList()));

    }

    @Override
    public CustomHandler newInstance() {
        USPTOAssignmentHandler handler = new USPTOAssignmentHandler();
        handler.init();
        return handler;
    }

    @Override
    public void save() {
        DataIngester.ingestAssets(queue,false);
    }


    private static void ingestData() {
        WebIterator iterator = new ZipFileIterator(new File("data/assignments"), "temp_dir_test",(a, b)->true);
        NestedHandler handler = new USPTOAssignmentHandler();
        iterator.applyHandlers(handler);
    }

    public static void main(String[] args) {
        SimilarPatentServer.loadAttributes();
        ingestData();
    }
}