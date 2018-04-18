package seeding.google.tech_tag;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import seeding.Database;
import seeding.google.postgres.Util;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class PredictTechTags {
    public static final File matrixFile = new File("/home/ehallmark/repos/poi/tech_tag_statistics_matrix.jobj");
    public static final File titleListFile = new File("/home/ehallmark/repos/poi/tech_tag_statistics_title_list.jobj");
    public static final File wordListFile = new File("/home/ehallmark/repos/poi/tech_tag_statistics_word_list.jobj");
    public static final File parentChildMapFile = new File("/home/ehallmark/repos/poi/tech_tag_statistics_parent_child_map.jobj");

    private static final Set<String> invalidTechnologies = new HashSet<>(
            Arrays.asList(
                    //"DATA PUBLISHING",
                    "SURFACE SCIENCE",
                    "SURFACE STRESS",
                    "POWER MOSFET",
                    "FRESH WATER",
                    //"POWER ELECTRONICS",
                    "SEAT BELT",
                    "MITSUBISHI RISE",
                    "SONDOR",
                    "LOCKHEED MARTIN",
                    "INTERNATIONAL VOLUNTEER ORGANIZATIONS",
                    "CHILD WELFARE",
                    "HETEROJUNCTION BIPOLAR TRANSISTOR",
                    //"DEVICE FILE",
                    "BUTEYKO METHOD",
                    "SPIRIT DATACINE",
                    "DRINKING WATER",
                    //"INDUSTRIAL PROCESSES",
                    "DISNEY SECOND SCREEN",
                    "FILM PRODUCTION",
                    "MOVIE PROJECTOR",
                    "NANOPHOTONIC RESONATOR",
                    //"COLOR",
                    //"HYDROGEN",
                    //"FULL BODY SCANNER",
                    //"DRIVER STEERING RECOMMENDATION",
                    "BIG DATA",
                    //"ALCOHOL",
                    "ART MEDIA",
                    "FILM",
                    "DYNABEADS",
                    "CHINESE COOKING TECHNIQUES",
                    "LITER OF LIGHT",
                    "WATER SUPPLY",
                    "VOLKSWAGEN GROUP",
                    "HIPPO WATER ROLLER",
                    "DEEP COLUMN STATIONS", // too small
                    "BMW",
                    "AMATEUR RADIO RECEIVERS",
                    "WATER MANAGEMENT",
                    "LIMELIGHT"
            )
    );


    private static final Function<String,String> technologyTransformer = tech -> {
        return tech;
    };

    public static void main(String[] args) throws Exception {
        final int batch = 500;
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        DefaultPipelineManager.setCudaEnvironment();

        INDArray matrixOld = (INDArray) Database.tryLoadObject(matrixFile);
        List<String> allTitlesList = (List<String>) Database.tryLoadObject(titleListFile);
        List<String> allWordsList = (List<String>) Database.tryLoadObject(wordListFile);
        Map<String,Set<String>> parentChildMap = (Map<String,Set<String>>) Database.tryLoadObject(parentChildMapFile);

        int validSize = allTitlesList.size();
        for(String title : allTitlesList) {
            if(invalidTechnologies.contains(title)) {
                validSize--;
            }
        }

        INDArray matrix = Nd4j.create(validSize,matrixOld.columns());
        AtomicInteger idx = new AtomicInteger(0);
        for(int i = 0; i < allTitlesList.size(); i++) {
            String title = allTitlesList.get(i);
            if(!invalidTechnologies.contains(title)) {
                matrix.putRow(idx.getAndIncrement(),matrixOld.getRow(i));
            }
        }
        matrixOld.cleanup();

        allTitlesList.removeAll(invalidTechnologies);
        for(String invalid : invalidTechnologies) {
            parentChildMap.remove(invalid);
        }
        parentChildMap.entrySet().forEach(e->{
            e.getValue().removeAll(invalidTechnologies);
            e.getValue().remove(e.getKey());
        });

        Map<String,Set<String>> childParentMap = new HashMap<>();
        parentChildMap.forEach((parent,children)->{
            children.forEach(child->{
                childParentMap.putIfAbsent(child,new HashSet<>());
                childParentMap.get(child).add(parent);
            });
        });

        List<String> allParentsList = new ArrayList<>(parentChildMap.keySet());
        List<String> allChildrenList = new ArrayList<>(childParentMap.keySet());
        List<String> childrenToRemove = new ArrayList<>();
        allChildrenList.forEach(child->{
            if(childParentMap.getOrDefault(child,Collections.emptySet()).size()>=parentChildMap.getOrDefault(child, Collections.emptySet()).size()) {
                allParentsList.remove(child);
            } else {
                childrenToRemove.add(child);
            }
        });
        allChildrenList.removeAll(childrenToRemove);

        System.out.println("Num parents: "+allParentsList.size());
        System.out.println("Num children: "+allChildrenList.size());
        System.out.println("Valid technologies: "+allTitlesList.size()+" out of "+matrixOld.rows());
        Map<String,Integer> wordToIndexMap = new HashMap<>();
        for(int i = 0; i < allWordsList.size(); i++) {
            wordToIndexMap.put(allWordsList.get(i),i);
        }

        Map<String,Integer> titleToIndexMap = new HashMap<>();
        for(int i = 0; i < allTitlesList.size(); i++) {
            titleToIndexMap.put(allTitlesList.get(i),i);
        }

        INDArray parentMatrixView = createMatrixView(matrix,allParentsList,titleToIndexMap,false);
        INDArray childMatrixView = createMatrixView(matrix,allChildrenList,titleToIndexMap,false);

        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select family_id,publication_number_full,description from big_query_patent_english_description");
        ps.setFetchSize(10);
        ResultSet rs = ps.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        Connection conn = Database.getConn();

        AtomicLong totalCnt = new AtomicLong(0);
        PreparedStatement insertDesign = conn.prepareStatement("insert into big_query_technologies (family_id,technology,secondary) values (?,'DESIGN','DESIGN') on conflict (family_id) do update set (technology,secondary)=('DESIGN','DESIGN')");
        PreparedStatement insertPlant = conn.prepareStatement("insert into big_query_technologies (family_id,technology,secondary) values (?,'BOTANY','PLANTS') on conflict (family_id) do update set (technology,secondary)=('BOTANY','BOTANY')");
        while(true) {
            int i = 0;
            INDArray vectors = Nd4j.create(matrix.columns(),batch);
            List<String> familyIds = new ArrayList<>(batch);
            String firstPub = null;
            String firstTech = null;
            String firstSecondary = null;
            for(; i < batch&&rs.next(); i++) {
                totalCnt.getAndIncrement();
                String familyId = rs.getString(1);
                familyIds.add(familyId);
                String publicationNumberFull = rs.getString(2);
                String publicationNumber = publicationNumberFull.substring(2);
                if(publicationNumber.startsWith("D")) {
                    // plant
                    insertDesign.setString(1, familyId);
                    insertDesign.executeUpdate();
                    i--;
                } else if (publicationNumber.startsWith("PP")) {
                    // design
                    insertPlant.setString(1, familyId);
                    insertPlant.executeUpdate();
                    i--;
                } else {
                    String text = rs.getString(3);
                    String[] content = Util.textToWordFunction.apply(text);
                    int found = 0;
                    float[] data = new float[matrix.columns()];
                    for (String word : content) {
                        Integer index = wordToIndexMap.get(word);
                        if (index != null) {
                            found++;
                            data[index]++;
                        }
                    }
                    if (found > 10) {
                        if(firstPub==null) firstPub=publicationNumberFull;
                        vectors.putColumn(i, Nd4j.create(data));
                        //System.out.println("Best tag for " + publicationNumber + ": " + tag);
                    } else {
                        i--;
                        continue;
                    }

                }

            }
            if(i==0) {
                break;
            }
            boolean breakAfter = false;
            if(i<batch) {
                breakAfter = true;
                vectors = vectors.get(NDArrayIndex.all(),NDArrayIndex.interval(0,i));
            }
            vectors.diviRowVector(vectors.norm2(0));
            INDArray primaryScores = parentMatrixView.mmul(vectors);
            int[] bestPrimaryIndices = Nd4j.argMax(primaryScores, 0).data().asInt();
            INDArray secondaryScores = childMatrixView.mmul(vectors);
            int[] bestSecondaryIndices = Nd4j.argMax(secondaryScores, 0).data().asInt();
            String insert = "insert into big_query_technologies (family_id,technology,secondary) values ? on conflict(family_id) do update set (technology,secondary)=(excluded.technology,excluded.secondary)";
            StringJoiner valueJoiner = new StringJoiner(",");
            for(int j = 0; j < i; j++) {
                StringJoiner innerJoiner = new StringJoiner("','","('","')");
                String familyId = familyIds.get(j);
                String tag = allParentsList.get(bestPrimaryIndices[j]);
                String secondary = allChildrenList.get(bestSecondaryIndices[j]);
                tag = technologyTransformer.apply(tag);
                secondary = technologyTransformer.apply(secondary);
                innerJoiner.add(familyId).add(tag).add(secondary);
                valueJoiner.add(innerJoiner.toString());
                if(firstTech==null) firstTech=tag;
                if(firstSecondary==null) firstSecondary=secondary;
                if (cnt.getAndIncrement() % 10000 == 9999) {
                    System.out.println("Finished: " + cnt.get() + " valid of " + totalCnt.get());
                    System.out.println("Sample "+firstPub+": " + firstTech+"; "+firstSecondary);
                    Database.commit();
                }
            }
            PreparedStatement insertPs = conn.prepareStatement(insert.replace("?",valueJoiner.toString()));
            insertPs.executeUpdate();
            if(breakAfter) {
                break;
            }
        }

        Database.commit();
        seedConn.commit();
        Database.close();
    }

    private static INDArray createMatrixView(INDArray full, List<String> elements, Map<String,Integer> indexMap, boolean columnwise) {
        int[] indices = elements.stream().mapToInt(e->indexMap.get(e)).toArray();
        return columnwise?full.getColumns(indices):full.getRows(indices);
    }
}
