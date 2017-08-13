package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import elasticsearch.DataIngester;
import lombok.Setter;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**

 */
public class USPTOAssignmentHandler extends NestedHandler {
    private static final AtomicLong cnt = new AtomicLong(0);
    private static final AtomicLong errors = new AtomicLong(0);
    @Setter
    protected static Collection<ComputableAttribute> computableAttributes;

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
        // application flags
        EndFlag documentFlag = new EndFlag("patent-assignment") {
            @Override
            public void save() {
                try {
                    debug(this, debug, null);
                    Map<String, Object> toIngest = new HashMap<>();
                    Map<String,Object> assignmentMap = getTransform(null);
                    toIngest.put(Constants.ASSIGNMENTS, assignmentMap);
                    Object reel = assignmentMap.get(Constants.REEL_NO);
                    Object frame = assignmentMap.get(Constants.FRAME_NO);
                    // get reel frame
                    if (reel == null || frame == null){
                        System.out.println("NO NAME!!!!!!!!!!");
                        if(errors.getAndIncrement()%10==0) {
                            System.out.println(errors.get());
                        }
                        return;
                    }
                    String reelFrame = reel.toString()+":"+frame.toString();
                    assignmentMap.put(Constants.REEL_FRAME, reelFrame);
                    AtomicReference<Collection<Object>> assets = new AtomicReference<>();
                    nestedEndFlags.forEach(endFlag -> {
                        List<Map<String, Object>> data = endFlag.dataQueue;
                        if (data.isEmpty() || endFlag.children.isEmpty()) return;
                        if (endFlag.isArray()) {
                            // add as array
                            if(endFlag.localName.equals("document-id")) {
                                assets.set(data.stream().map(map -> map.values().stream().findAny().orElse(null)).filter(d -> d != null).collect(Collectors.toList()));
                                assignmentMap.put(endFlag.dbName,assets.get());
                            } else {
                                toIngest.put(endFlag.dbName, data.stream().map(map -> map.values().stream().findAny().orElse(null)).filter(d -> d != null).collect(Collectors.toList()));
                            }
                        } else {
                            toIngest.put(endFlag.dbName, data.stream().filter(map->map.size()>0).collect(Collectors.toList()));
                        }
                    });
                    // update computable attrs
                    if(computableAttributes!=null && assets.get() != null) {
                        computableAttributes.forEach(attr -> {
                            // for each patent or application
                            assets.get().forEach(name->{
                                if (Database.isApplication(name.toString())) {
                                    attr.handleApplicationData(name.toString(), toIngest);
                                } else {
                                    attr.handlePatentData(name.toString(), toIngest);
                                }
                            });
                        });
                    }
                    synchronized (USPTOAssignmentHandler.class) {
                        if(cnt.getAndIncrement() % batchSize == batchSize-1) {
                            System.out.println(cnt.get());
                        }
                        // for each patent or application
                        if(assets.get()!=null) {
                            assets.get().forEach(asset->{
                                saveElasticSearch(asset.toString(),toIngest);
                            });
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
        documentFlag.addChild(Flag.integerFlag("page-count", Constants.PAGE_COUNT, documentFlag));

        Flag correspondFlag = Flag.simpleFlag("correspondent", Constants.CORRESPONDENT, documentFlag);
        documentFlag.addChild(correspondFlag);
        correspondFlag.addChild(Flag.simpleFlag("name", Constants.FULL_NAME, documentFlag));
        correspondFlag.addChild(Flag.simpleFlag("address-1", Constants.ADDRESS_1, documentFlag));
        correspondFlag.addChild(Flag.simpleFlag("address-2", Constants.ADDRESS_2, documentFlag));
        correspondFlag.addChild(Flag.simpleFlag("address-3", Constants.ADDRESS_3, documentFlag));

        // assignee
        EndFlag assigneeFlag = new EndFlag("patent-assignee") {
            {
                dbName = Constants.LATEST_ASSIGNEE;
            }
            @Override
            public void save() {
                dataQueue.add(getTransform(null));
            }
        };
        endFlags.add(assigneeFlag);

        assigneeFlag.addChild(Flag.simpleFlag("name",Constants.ASSIGNEE, assigneeFlag).withTransformationFunction(Flag.assigneeTransformationFunction));
        assigneeFlag.addChild(Flag.simpleFlag("address-1",Constants.ADDRESS_1, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("address-2",Constants.ADDRESS_2, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("address-3",Constants.ADDRESS_3, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("city",Constants.CITY, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("state",Constants.STATE, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("postcode",Constants.POSTAL_CODE, assigneeFlag));
        assigneeFlag.addChild(Flag.simpleFlag("country",Constants.COUNTRY, assigneeFlag));

        // assignor
        EndFlag assignorFlag = new EndFlag("patent-assignor") {
            {
                dbName = Constants.ASSIGNORS;
            }
            @Override
            public void save() {
                dataQueue.add(getTransform(null));
            }
        };
        endFlags.add(assignorFlag);

        assignorFlag.addChild(Flag.simpleFlag("name",Constants.FULL_NAME, assignorFlag).withTransformationFunction(Flag.assigneeTransformationFunction));
        assignorFlag.addChild(Flag.dateFlag("execution-date", Constants.EXECUTION_DATE, assignorFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));
        assignorFlag.addChild(Flag.dateFlag("execution-date", Constants.EXECUTION_DATE, assignorFlag).withTransformationFunction(f->s->{
            Object formatted = Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE).apply(f).apply(s);
            if(formatted!=null) assigneeFlag.getDataMap().put(f, formatted.toString());
            return null;
        }).setIsForeign(true));

        // assignor
        EndFlag assetsFlag = new EndFlag("document-id") {
            {
                isArray=true;
                dbName = Constants.NAME;
            }
            @Override
            public void save() {
                dataQueue.add(getTransform(null));
            }
        };
        endFlags.add(assetsFlag);

        assetsFlag.addChild(Flag.simpleFlag("doc-number",Constants.NAME, assetsFlag).withTransformationFunction(Flag.unknownDocumentHandler));
        assetsFlag.addChild(Flag.simpleFlag("country",Constants.COUNTRY, assetsFlag));
        assetsFlag.addChild(Flag.simpleFlag("kind",Constants.DOC_KIND, assetsFlag));
        assetsFlag.addChild(Flag.dateFlag("date", Constants.RECORDED_DATE, assetsFlag).withTransformationFunction(Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE)));

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
        if (computableAttributes != null) {
            computableAttributes.forEach(attr -> {
                attr.save();
            });
        }
    }

    private void saveElasticSearch(String name, Map<String,Object> doc) {
        DataIngester.ingestBulk(name,doc,false);
    }



}