package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import com.google.gson.Gson;
import elasticsearch.DataIngester;
import lombok.Setter;
import org.bson.Document;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**

 */
public class USPTOAssignmentHandler extends NestedHandler {
    private static final AtomicLong cnt = new AtomicLong(0);
    private static final AtomicLong errors = new AtomicLong(0);
    private static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    private static final FilingToAssetMap filingToAssetMap = new FilingToAssetMap();
    @Setter
    private static Collection<ComputableAttribute> computableAttributes = Arrays.asList(new AssetToAssigneeMap());
    static {
        computableAttributes.forEach(attr->{
            attr.initMaps();
        });
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
        int batchSize = 10000;
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
                    // get date
                    Object date = assignmentMap.get(Constants.RECORDED_DATE);
                    if(date == null || LocalDate.parse(date.toString(),DateTimeFormatter.ISO_DATE).isBefore(LocalDate.now().minusYears(25))) {
                        return;
                    }
                    String reelFrame = reel.toString()+":"+frame.toString();
                    assignmentMap.put(Constants.REEL_FRAME, reelFrame);
                    AtomicReference<Collection<String>> assets = new AtomicReference<>(new HashSet<>());
                    nestedEndFlags.forEach(endFlag -> {
                        List<Map<String, Object>> data = endFlag.dataQueue;
                        if (data.isEmpty() || endFlag.children.isEmpty()) return;
                        if (endFlag.isArray()) {
                            // add as array
                            if(endFlag.localName.equals("document-id")) {
                                assets.set(data.stream().map(map -> (String) map.values().stream().findAny().orElse(null)).filter(d -> d != null).collect(Collectors.toList()));
                                assignmentMap.put(endFlag.dbName,assets.get());
                            } else {
                                toIngest.put(endFlag.dbName, data.stream().map(map -> map.values().stream().findAny().orElse(null)).filter(d -> d != null).collect(Collectors.toList()));
                            }
                        } else {
                            toIngest.put(endFlag.dbName, data.stream().filter(map->map.size()>0).collect(Collectors.toList()));
                        }
                    });
                    // update computable attrs
                    List<String> allAssets = assets.get().stream().flatMap(asset->{
                        Set<String> set = new HashSet<>();
                        if(asset.contains("/")) {
                            set.addAll(filingToAssetMap.getPatentDataMap().getOrDefault(asset,Collections.emptyList()));
                            set.addAll(filingToAssetMap.getApplicationDataMap().getOrDefault(asset,Collections.emptyList()));
                        } else {
                            String filing = assetToFilingMap.getPatentDataMap().getOrDefault(asset,assetToFilingMap.getApplicationDataMap().get(asset));
                            if(filing!=null) {
                                set.addAll(filingToAssetMap.getPatentDataMap().getOrDefault(filing,Collections.emptyList()));
                                set.addAll(filingToAssetMap.getApplicationDataMap().getOrDefault(filing,Collections.emptyList()));
                            }
                            set.add(asset);
                        }
                        return set.stream();
                    }).distinct().collect(Collectors.toList());
                    if(computableAttributes!=null) {
                        computableAttributes.forEach(attr -> {

                            // for each patent or application
                            allAssets.forEach(name->{
                                if (Database.isApplication(name)) {
                                    attr.handleApplicationData(name, toIngest);
                                } else {
                                    attr.handlePatentData(name, toIngest);
                                }
                            });
                        });
                    }

                    synchronized (USPTOAssignmentHandler.class) {
                        if(cnt.getAndIncrement() % batchSize == batchSize-1) {
                            System.out.println(cnt.get());
                        }
                        // for each patent or application
                        if(allAssets.size()>0) {
                            saveElasticSearch(allAssets,toIngest);
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
            //System.out.println("Found execution date: "+formatted);
            if(formatted!=null) {
                documentFlag.addData(Constants.EXECUTION_DATE, formatted.toString());
            }
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

    private void saveElasticSearch(List<String> ids, Map<String,Object> doc) {
        boolean debug = true; // TODO GET RID OF DEBUG!!
        //System.out.println("Full DOC: "+new Gson().toJson(doc));
        // get reel frame for id
        Map<String,Object> assignmentMap = (Map<String,Object>)doc.get(Constants.ASSIGNMENTS);
        if(assignmentMap!=null) {
            String reelFrame = (String) assignmentMap.get(Constants.REEL_FRAME);
            if(reelFrame!=null) {
                Document assetQuery = new Document("_id",new Document("$in", ids));
                // update reel frames array
                if (!debug) DataIngester.updateMongoArray(DataIngester.PARENT_TYPE_NAME, assetQuery, Constants.REEL_FRAME, reelFrame, Constants.REEL_FRAME, reelFrame);

                // update complex data
                Map<String,Object> mergedDataMap = new HashMap<>();

                mergeDataMapHelper(mergedDataMap, reelFrame, Constants.REEL_FRAME);

                List<Map<String, Object>> latestAssigneeData = (List<Map<String, Object>>) doc.get(Constants.LATEST_ASSIGNEE);
                if (latestAssigneeData != null && latestAssigneeData.size() > 0) {
                    List<String> assignees = latestAssigneeData.stream().map(map->map.get(Constants.ASSIGNEE)).filter(assignee->assignee!=null).map(obj->obj.toString()).collect(Collectors.toList());
                    if (assignees.size()>0) mergeDataMapHelper(mergedDataMap, assignees, Constants.ASSIGNEE);
                }

                // add assignor data
                List<Map<String, Object>> latestAssignorData = (List<Map<String, Object>>) doc.get(Constants.ASSIGNORS);
                if (latestAssignorData != null && latestAssignorData.size() > 0) {
                    List<String> assignors = latestAssignorData.stream().map(map->map.get(Constants.FULL_NAME)).filter(assignor->assignor!=null).map(obj->obj.toString()).collect(Collectors.toList());
                    if (assignors.size()>0) mergeDataMapHelper(mergedDataMap, assignors, Constants.ASSIGNOR);
                }
                // add conveyance text (helpful to fiend liens)
                Object conveyanceText = assignmentMap.get(Constants.CONVEYANCE_TEXT);
                if (conveyanceText!=null) {
                    mergeDataMapHelper(mergedDataMap, conveyanceText, Constants.CONVEYANCE_TEXT);
                }

                Object recordedDate = assignmentMap.get(Constants.RECORDED_DATE);
                if (recordedDate!=null) {
                    mergeDataMapHelper(mergedDataMap, recordedDate, Constants.RECORDED_DATE);
                }

                Object executionDate = assignmentMap.get(Constants.EXECUTION_DATE);
                if (recordedDate!=null) {
                    mergeDataMapHelper(mergedDataMap, executionDate, Constants.EXECUTION_DATE);
                }

                Object correspondentName = doc.get(Constants.FULL_NAME);
                if(correspondentName!=null) {
                    mergeDataMapHelper(mergedDataMap, correspondentName, Constants.CORRESPONDENT);
                }
                Object correspondentAddress1 = doc.get(Constants.ADDRESS_1);
                Object correspondentAddress2 = doc.get(Constants.ADDRESS_2);
                Object correspondentAddress3 = doc.get(Constants.ADDRESS_3);
                List<String> correspondentAddresses = Stream.of(correspondentAddress1,correspondentAddress2,correspondentAddress3)
                        .filter(address->address!=null)
                        .map(address->address.toString())
                        .collect(Collectors.toList());
                if(correspondentAddresses.size() > 0) {
                    String correspondentAddress = String.join(" ",correspondentAddresses);
                    mergeDataMapHelper(mergedDataMap,correspondentAddress,Constants.CORRESPONDENT_ADDRESS);
                }


                if (mergedDataMap.size()>0) {
                    if(debug) {
                        System.out.println("   FOUND: "+new Gson().toJson(mergedDataMap));
                    } else {
                        DataIngester.updateMongoArray(DataIngester.PARENT_TYPE_NAME, assetQuery, Constants.ASSIGNMENTS, mergedDataMap, Constants.ASSIGNMENTS+"."+Constants.REEL_FRAME, reelFrame);
                    }
                }
            }
        }
    }

    private static void mergeDataMapHelper(Map<String,Object> mergedMap, Object data, String fieldName) {
        mergedMap.put(fieldName, data);
    }


}