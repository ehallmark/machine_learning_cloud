package seeding.google.tech_tag;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingModel;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.api.Layer;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;
import seeding.google.postgres.Util;
import seeding.google.word2vec.Word2VecManager;

import java.io.File;
import java.sql.Array;
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
    public static final File titleToTextMapFile = new File("/home/ehallmark/repos/poi/tech_tag_title_to_text_map.jobj");

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
                    "VEHICLE PARTS",
                    "SPIRIT DATACINE",
                    "DRINKING WATER",
                    "DENDRITIC SPINE",
                    "INTERSPIRO DCSC",
                    "AZEOTROPE",
                    "WINDSCREEN WIPER",
                    "MATERIALS SCIENCE",
                    //"INDUSTRIAL PROCESSES",
                    "DISNEY SECOND SCREEN",
                    "FILM PRODUCTION",
                    "MOVIE PROJECTOR",
                    "NANOPHOTONIC RESONATOR",
                    "EMERGING TECHNOLOGIES",
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
                    "INTERNET TERMINOLOGY",
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

        Word2Vec word2Vec = Word2VecManager.getOrLoadManager();
        RNNTextEncodingPipelineManager pipelineManager = RNNTextEncodingPipelineManager.getOrLoadManager(true);
        pipelineManager.runPipeline(false,false,false,false,-1,false);
        RNNTextEncodingModel model = (RNNTextEncodingModel)pipelineManager.getModel();

        // create rnn vectors for wiki articles or add to invalidTechnologies if unable
        Map<String,String[]> titleToWordsMap = (Map<String,String[]>)Database.tryLoadObject(titleToTextMapFile);
        List<String> titlesForRnn = new ArrayList<>(titleToWordsMap.keySet());
        List<INDArray> rnnVectorsList = new ArrayList<>();
        for(int i = 0; i < titlesForRnn.size(); i++) {
            String title = titlesForRnn.get(i);
            String[] text = titleToWordsMap.get(title);
            INDArray wordVectors = word2Vec.getWordVectors(Arrays.asList(text));
            if(wordVectors.rows()>10) {
                wordVectors = wordVectors.transpose();
                wordVectors = wordVectors.reshape(1,word2Vec.getLayerSize(),wordVectors.columns());
                INDArray encoding = model.getNet().getLayers()[0].activate(wordVectors, Layer.TrainingMode.TEST);
                rnnVectorsList.add(encoding);
            } else {
                invalidTechnologies.add(title);
            }
        }
        INDArray rnnMatrix = Nd4j.vstack(rnnVectorsList);

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

        List<Pair<Integer,Integer>> parentChildCombinations = new ArrayList<>();
        for(int i = 0; i < allParentsList.size(); i++) {
            String parent = allParentsList.get(i);
            Set<String> children = parentChildMap.getOrDefault(parent,new HashSet<>());
            for(String child : children) {
                int j = allChildrenList.indexOf(child);
                if(j>=0) {
                    parentChildCombinations.add(new Pair<>(i,j));
                }
            }
        }

        System.out.println("Num parents: "+allParentsList.size());
        System.out.println("Num children: "+allChildrenList.size());
        System.out.println("Valid technologies: "+allTitlesList.size()+" out of "+matrixOld.rows());
        System.out.println("Valid rnn encodings: "+rnnMatrix.rows()+" out of "+matrixOld.rows());

        if(rnnMatrix.rows()!=allTitlesList.size()) {
            throw new RuntimeException("Expected rnn matrix size to equal all titles list size, but "+rnnMatrix.rows()+" != "+allTitlesList.size());
        }
        
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

        INDArray childRnnView = createMatrixView(rnnMatrix,allChildrenList,titleToIndexMap,false);

        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select family_id,publication_number_full,abstract,description,rnn_enc from big_query_patent_english_abstract as a left outer join big_query_patent_english_description as d on (a.family_id=d.family_id) left outer join big_query_embedding2 as e on (d.family_id=e.family_id)");
        ps.setFetchSize(10);
        ResultSet rs = ps.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        Connection conn = Database.getConn();

        AtomicLong totalCnt = new AtomicLong(0);
        PreparedStatement insertDesign = conn.prepareStatement("insert into big_query_technologies (family_id,technology,secondary) values (?,'DESIGN','DESIGN') on conflict (family_id) do update set (technology,secondary)=('DESIGN','DESIGN')");
        PreparedStatement insertPlant = conn.prepareStatement("insert into big_query_technologies (family_id,technology,secondary) values (?,'BOTANY','PLANTS') on conflict (family_id) do update set (technology,secondary)=('BOTANY','BOTANY')");
        while(true) {
            int i = 0;
            INDArray abstractVectors = Nd4j.create(matrix.columns(),batch);
            INDArray rnnVectors = Nd4j.create(pipelineManager.getEncodingSize(),batch);
            INDArray descriptionVectors = Nd4j.create(matrix.columns(),batch);
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
                    String[] abstractText = Util.textToWordFunction.apply(rs.getString(3));
                    String[] descriptionText = Util.textToWordFunction.apply(rs.getString(4));
                    Array sqlEncoding = rs.getArray(5);
                    float[] rnnVec = new float[pipelineManager.getEncodingSize()];
                    if(sqlEncoding!=null) {
                        final Number[] encoding = (Number[]) sqlEncoding.getArray();
                        for(int j = 0; j < encoding.length; j++) {
                            rnnVec[j]=encoding[j].floatValue();
                        }
                    }
                    int found = 0;
                    float[] abstractData = new float[matrix.columns()];
                    float[] descriptionData = new float[matrix.columns()];
                    if(abstractText!=null) {
                        for (String word : abstractText) {
                            Integer index = wordToIndexMap.get(word);
                            if (index != null) {
                                found++;
                                abstractData[index]++;
                            }
                        }
                    }
                    if(descriptionText!=null) {
                        for(String word : descriptionText) {
                            Integer index = wordToIndexMap.get(word);
                            if (index != null) {
                                found++;
                                descriptionData[index]++;
                            }
                        }
                    }
                    if (found > 10) {
                        abstractVectors.putColumn(i, Nd4j.create(abstractData));
                        descriptionVectors.putColumn(i, Nd4j.create(descriptionData));
                        rnnVectors.putColumn(i, Nd4j.create(rnnVec));
                        if(firstPub==null) firstPub=publicationNumberFull;
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
                abstractVectors = abstractVectors.get(NDArrayIndex.all(),NDArrayIndex.interval(0,i));
                descriptionVectors = descriptionVectors.get(NDArrayIndex.all(),NDArrayIndex.interval(0,i));
                rnnVectors = rnnVectors.get(NDArrayIndex.all(),NDArrayIndex.interval(0,i));
            }
            abstractVectors.diviRowVector(abstractVectors.norm2(0));
            descriptionVectors.diviRowVector(descriptionVectors.norm2(0));
            rnnVectors.diviRowVector(rnnVectors.norm2(0));

            INDArray primaryScores = parentMatrixView.mmul(abstractVectors).addi(parentMatrixView.mmul(descriptionVectors));
            INDArray secondaryScores = childMatrixView.mmul(abstractVectors).addi(childMatrixView.mmul(descriptionVectors)).addi(childRnnView.mmul(rnnVectors));
            String insert = "insert into big_query_technologies (family_id,technology,secondary) values ? on conflict(family_id) do update set (technology,secondary)=(excluded.technology,excluded.secondary)";
            StringJoiner valueJoiner = new StringJoiner(",");
            for(int j = 0; j < i; j++) {
                StringJoiner innerJoiner = new StringJoiner("','","('","')");
                String familyId = familyIds.get(j);
                float[] primary = primaryScores.getRow(j).data().asFloat();
                float[] secondary = secondaryScores.getRow(j).data().asFloat();
                Pair<Integer,Integer> top = parentChildCombinations.parallelStream().map(p->{
                    return new Pair<>(p,primary[p.getFirst()]+secondary[p.getSecond()]);
                }).sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(1).map(p->p.getFirst()).findFirst().orElse(null);
                if(top!=null) {
                    String tag = allParentsList.get(top.getFirst());
                    String secondaryTag = allChildrenList.get(top.getSecond());
                    tag = technologyTransformer.apply(tag);
                    secondaryTag = technologyTransformer.apply(secondaryTag);
                    innerJoiner.add(familyId).add(tag).add(secondaryTag);
                    valueJoiner.add(innerJoiner.toString());
                    if (firstTech == null) firstTech = tag;
                    if (firstSecondary == null) firstSecondary = secondaryTag;
                }
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
