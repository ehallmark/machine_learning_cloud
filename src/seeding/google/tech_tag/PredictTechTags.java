package seeding.google.tech_tag;

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
    public static final File matrixFile = new File("tech_tag_statistics_matrix.jobj");
    public static final File titleListFile = new File("tech_tag_statistics_title_list.jobj");
    public static final File wordListFile = new File("tech_tag_statistics_word_list.jobj");


    private static final Set<String> invalidTechnologies = new HashSet<>(
            Arrays.asList(
                    "DATA PUBLISHING",
                    "SURFACE SCIENCE",
                    "FRESH WATER",
                    "POWER ELECTRONICS",
                    "MITSUBISHI RISE",
                    "SONDOR",
                    "LOCKHEED MARTIN",
                    "INTERNATIONAL VOLUNTEER ORGANIZATIONS",
                    "CHILD WELFARE",
                    "HETEROJUNCTION BIPOLAR TRANSISTOR",
                    "DEVICE FILE",
                    "BUTEYKO METHOD",
                    "SPIRIT DATACINE",
                    "DRINKING WATER",
                    "INDUSTRIAL PROCESSES",
                    "DISNEY SECOND SCREEN",
                    "COLOR",
                    "HYDROGEN",
                    "FULL BODY SCANNER",
                    "DRIVER STEERING RECOMMENDATION",
                    "BIG DATA",
                    "ALCOHOL",
                    "DYNABEADS",
                    "CHINESE COOKING TECHNIQUES",
                    "LITER OF LIGHT",
                    "WATER SUPPLY",
                    "VOLKSWAGEN GROUP",
                    "HIPPO WATER ROLLER",
                    "DEEP COLUMN STATIONS", // too small
                    "BMW",
                    "AMATEUR RADIO RECEIVERS",
                    "LIMELIGHT"
            )
    );

    private static final Map<String,String> techTransformationMap = new HashMap<>();
    static {
        // FOR EXAMPE ...
        // techTransformationMap.put("HYDROGEN VEHICLE","VEHICLE");
    }
    private static final Function<String,String> technologyTransformer = tech -> {
        return techTransformationMap.getOrDefault(tech, tech);
    };

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        INDArray matrixOld = (INDArray) Database.tryLoadObject(matrixFile);
        List<String> allTitlesList = (List<String>) Database.tryLoadObject(titleListFile);
        List<String> allWordsList = (List<String>) Database.tryLoadObject(wordListFile);
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
        System.out.println("Valid technologies: "+allTitlesList.size()+" out of "+matrixOld.rows());
        Map<String,Integer> wordToIndexMap = new HashMap<>();
        for(int i = 0; i < allWordsList.size(); i++) {
            wordToIndexMap.put(allWordsList.get(i),i);
        }
        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select family_id,publication_number_full,abstract from big_query_patent_english_abstract");
        ps.setFetchSize(10);
        ResultSet rs = ps.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        Connection conn = Database.getConn();
        final int batch = 500;
        AtomicLong totalCnt = new AtomicLong(0);
        PreparedStatement insertDesign = conn.prepareStatement("insert into big_query_technologies (family_id,technology) values (?,'DESIGN') on conflict (family_id) do update set technology='DESIGN'");
        PreparedStatement insertPlant = conn.prepareStatement("insert into big_query_technologies (family_id,technology) values (?,'BOTANY') on conflict (family_id) do update set technology='BOTANY'");
        while(true) {
            int i = 0;
            INDArray vectors = Nd4j.create(matrix.columns(),batch);
            List<String> familyIds = new ArrayList<>(batch);
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
                        vectors.putColumn(i, Nd4j.create(data));
                        //System.out.println("Best tag for " + publicationNumber + ": " + tag);
                    } else {
                        i--;
                        continue;
                    }

                }
                if (cnt.getAndIncrement() % 10000 == 9999) {
                    System.out.println("Finished: " + cnt.get() + " valid of " + totalCnt.get());
                    Database.commit();
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
            INDArray scores = matrix.mmul(vectors);
            int[] bestIndices = Nd4j.argMax(scores, 0).data().asInt();
            String insert = "insert into big_query_technologies (family_id,technology) values ? on conflict(family_id) do update set technology=excluded.technology";
            StringJoiner valueJoiner = new StringJoiner(",");
            for(int j = 0; j < i; j++) {
                StringJoiner innerJoiner = new StringJoiner("','","('","')");
                int bestIdx = bestIndices[j];
                String familyId = familyIds.get(j);
                String tag = allTitlesList.get(bestIdx);
                tag = technologyTransformer.apply(tag);
                innerJoiner.add(familyId).add(tag);
                valueJoiner.add(innerJoiner.toString());
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
}
