package seeding.google.tech_tag;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingModel;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.BooleanIndexing;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.indexing.conditions.Conditions;
import org.nd4j.linalg.indexing.functions.Value;
import org.nd4j.linalg.ops.transforms.Transforms;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
                    "EMERGING TECHNOLOGY",
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
        final Random random = new Random(235211);
        final int batch = 1000;
        final int maxTags = 3;
        final double minScore = 0.2;
        final int rnnLimit = 128;
        final int rnnSamples = 8;
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        DefaultPipelineManager.setCudaEnvironment();

        final Word2Vec word2Vec = Word2VecManager.getOrLoadManager();
        final RNNTextEncodingPipelineManager pipelineManager = RNNTextEncodingPipelineManager.getOrLoadManager(true);
        pipelineManager.runPipeline(false,false,false,false,-1,false);
        final RNNTextEncodingModel model = (RNNTextEncodingModel)pipelineManager.getModel();

        INDArray matrixOld = (INDArray) Database.tryLoadObject(matrixFile);
        List<String> allTitlesList = (List<String>) Database.tryLoadObject(titleListFile);
        List<String> allWordsList = (List<String>) Database.tryLoadObject(wordListFile);
        Map<String,Set<String>> parentChildMap = (Map<String,Set<String>>) Database.tryLoadObject(parentChildMapFile);
        if(matrixOld.rows()!=allTitlesList.size()) {
            throw new RuntimeException("Invalid configuration. Matrix rows: "+matrixOld.rows()+", All Titles: "+allTitlesList.size());
        }
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

        System.out.println("Num parents before: "+allParentsList.size());
        System.out.println("Num children before: "+allChildrenList.size());

        allParentsList.removeIf(i->!allTitlesList.contains(i));
        allChildrenList.removeIf(i->!allTitlesList.contains(i));
        List<String> childrenToRemove = new ArrayList<>();
        allChildrenList.forEach(child->{
            if(childParentMap.getOrDefault(child,Collections.emptySet()).size()>=parentChildMap.getOrDefault(child, Collections.emptySet()).size()) {
                allParentsList.remove(child);
            } else {
                childrenToRemove.add(child);
            }
        });
        allChildrenList.removeAll(childrenToRemove);
        allChildrenList.removeAll(invalidTechnologies);

        // create rnn vectors for wiki articles or add to invalidTechnologies if unable
        Map<String,String[]> titleToWordsMap = (Map<String,String[]>)Database.tryLoadObject(titleToTextMapFile);
        INDArray rnnMatrix = Nd4j.create(allChildrenList.size(),pipelineManager.getEncodingSize());
        INDArray wordMatrix = Nd4j.create(allChildrenList.size(),word2Vec.getLayerSize());
        for(int i = 0; i < allChildrenList.size(); i++) {
            String title = allChildrenList.get(i);
            INDArray encoding = null;
            INDArray wordVec = null;
            if(titleToWordsMap.containsKey(title)) {
                String[] text = Stream.of(titleToWordsMap.get(title))
                        .filter(word -> word2Vec.hasWord(word))
                        .toArray(s -> new String[s]);

                if (text.length > 5) {
                    List<String> labels = IntStream.range(0,rnnLimit).mapToObj(j->text[random.nextInt(text.length)])
                            .collect(Collectors.toList());
                    wordVec = Transforms.unitVec(word2Vec.getWordVectors(labels).mean(0));
                    if (text.length > rnnLimit) {
                        INDArray features = Nd4j.create(rnnSamples, word2Vec.getLayerSize(), rnnLimit);
                        for (int j = 0; j < rnnSamples; j++) {
                            int r = random.nextInt(text.length - rnnLimit);
                            String[] textSample = Arrays.copyOfRange(text, r, r + rnnLimit);
                            INDArray wordVectors = word2Vec.getWordVectors(Arrays.asList(textSample));

                            features.get(NDArrayIndex.point(j), NDArrayIndex.all(), NDArrayIndex.all()).assign(wordVectors.transpose());
                        }
                        encoding = Transforms.unitVec(model.encode(features).mean(0));
                    } else {
                        INDArray wordVectors = word2Vec.getWordVectors(Arrays.asList(text));
                        wordVectors = wordVectors.transpose();
                        wordVectors = wordVectors.reshape(1, word2Vec.getLayerSize(), wordVectors.columns());
                        encoding = Transforms.unitVec(model.encode(wordVectors));
                    }
                }
            }
            if(encoding==null) {
                rnnMatrix.get(NDArrayIndex.point(i),NDArrayIndex.all()).assign(0);
            } else {
                rnnMatrix.putRow(i, encoding);
            }
            if(wordVec==null) {
                wordMatrix.get(NDArrayIndex.point(i),NDArrayIndex.all()).assign(0);
            } else {
                wordMatrix.putRow(i, wordVec);
            }
            if(i%100==99) System.out.println("Finished "+(1+i)+" out of "+allChildrenList.size());
        }

        System.out.println("Num parents: "+allParentsList.size());
        System.out.println("Num children: "+allChildrenList.size());
        System.out.println("Valid technologies: "+allTitlesList.size()+" out of "+matrixOld.rows());
        System.out.println("Valid rnn encodings: "+rnnMatrix.rows()+" out of "+matrixOld.rows());

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

        if(rnnMatrix.rows()!=allChildrenList.size()) {
            throw new RuntimeException("Expected rnn matrix size to equal all children list size, but "+rnnMatrix.rows()+" != "+allChildrenList.size());
        }
        
        Map<String,Integer> wordToIndexMap = new HashMap<>();
        for(int i = 0; i < allWordsList.size(); i++) {
            wordToIndexMap.put(allWordsList.get(i),i);
        }

        Map<String,Integer> titleToIndexMap = new HashMap<>();
        for(int i = 0; i < allTitlesList.size(); i++) {
            titleToIndexMap.put(allTitlesList.get(i),i);
        }

        for(String child : allChildrenList) {
            if(!allTitlesList.contains(child)) {
                System.out.println("Child not found in titles: "+child);
            }
        }

        INDArray parentMatrixView = createMatrixView(matrix,allParentsList,titleToIndexMap,false);
        INDArray childMatrixView = createMatrixView(matrix,allChildrenList,titleToIndexMap,false);

        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select a.family_id,a.publication_number_full,abstract,description,rnn_enc from big_query_patent_english_abstract as a left outer join big_query_patent_english_description as d on (a.family_id=d.family_id) left outer join big_query_embedding2 as e on (d.family_id=e.family_id)");
        System.out.println("PS: "+ps.toString());
        ps.setFetchSize(10);
        ResultSet rs = ps.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        Connection conn = Database.getConn();

        AtomicLong totalCnt = new AtomicLong(0);
        PreparedStatement insertDesign = conn.prepareStatement("insert into big_query_technologies2 (family_id,publication_number_full,technology,technology2) values (?,?,'DESIGN','DESIGN') on conflict (family_id) do update set (technology,technology2,technology3)=('DESIGN','DESIGN', null)");
        PreparedStatement insertPlant = conn.prepareStatement("insert into big_query_technologies2 (family_id,publication_number_full,technology,technology2) values (?,?,'BOTANY','PLANTS') on conflict (family_id) do update set (technology,technology2,technology3)=('BOTANY','PLANTS', null)");

        System.out.println("Starting to iterate...");
        while(true) {
            int i = 0;
            INDArray abstractVectors = Nd4j.create(matrix.columns(),batch);
            INDArray rnnVectors = Nd4j.create(pipelineManager.getEncodingSize(),batch);
            INDArray wordVectors = Nd4j.create(word2Vec.getLayerSize(),batch);
            INDArray descriptionVectors = Nd4j.create(matrix.columns(),batch);
            List<String> familyIds = new ArrayList<>(batch);
            List<String> pubNums = new ArrayList<>(batch);
            String firstPub = null;
            String firstTech = null;
            String firstSecondary = null;
            List<String> firstThird = null;
            for(; i < batch&&rs.next(); i++) {
                totalCnt.getAndIncrement();
                if(totalCnt.get()%10000==9999) {
                    System.out.println("Seen total: "+totalCnt.get());
                }
                String familyId = rs.getString(1);
                String publicationNumberFull = rs.getString(2);
                String publicationNumber = publicationNumberFull.substring(2);
                if(publicationNumber.startsWith("D")) {
                    // plant
                    insertDesign.setString(1, familyId);
                    insertDesign.setString(2,publicationNumberFull);
                    insertDesign.executeUpdate();
                    i--;
                } else if (publicationNumber.startsWith("PP")) {
                    // design
                    insertPlant.setString(1, familyId);
                    insertPlant.setString(2,publicationNumberFull);
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
                        INDArray _wv = word2Vec.getWordVectors(Arrays.asList(abstractText));
                        if(_wv.rows()>5) {
                            wordVectors.putColumn(i, _wv.mean(0));
                        } else {
                            wordVectors.get(NDArrayIndex.all(),NDArrayIndex.point(i)).assign(0);
                        }
                    } else {
                        wordVectors.get(NDArrayIndex.all(),NDArrayIndex.point(i)).assign(0);
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
                        familyIds.add(familyId);
                        pubNums.add(publicationNumberFull);
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
                wordVectors = wordVectors.get(NDArrayIndex.all(),NDArrayIndex.interval(0,i));
            }
            INDArray aNorm = abstractVectors.norm2(0);
            INDArray dNorm = descriptionVectors.norm2(0);
            INDArray rNorm = rnnVectors.norm2(0);
            INDArray wNorm = wordVectors.norm2(0);
            // clear NaNs
            BooleanIndexing.applyWhere(aNorm, Conditions.equals(0), new Value(1));
            BooleanIndexing.applyWhere(dNorm, Conditions.equals(0), new Value(1));
            BooleanIndexing.applyWhere(rNorm, Conditions.equals(0), new Value(1));
            BooleanIndexing.applyWhere(wNorm, Conditions.equals(0), new Value(1));

            abstractVectors.diviRowVector(aNorm);
            descriptionVectors.diviRowVector(dNorm);
            rnnVectors.diviRowVector(rNorm);
            wordVectors.diviRowVector(wNorm);

            // predict keywords
            Map<String,Set<String>> keywordMap = new HashMap<>();
            Consumer<Pair<String,Set<String>>> keywordConsumer = pair -> {
                keywordMap.put(pair.getKey(),pair.getSecond());
            };
            //keyphrasePredictionModel.predict(familyIds,wordVectors,rnnVectors,maxTags, minScore, keywordConsumer);

            INDArray primaryScores = parentMatrixView.mmul(abstractVectors).addi(parentMatrixView.mmul(descriptionVectors));
            //INDArray secondaryScores = childMatrixView.mmul(abstractVectors).addi(childMatrixView.mmul(descriptionVectors)).addi(wordMatrix.mmul(wordVectors)).addi(rnnMatrix.mmul(rnnVectors));
            INDArray secondaryScores = rnnMatrix.mmul(rnnVectors).addi(wordMatrix.mmul(wordVectors));

            String insert = "insert into big_query_technologies2 (family_id,publication_number_full,technology,technology2,technology3) values ? on conflict(family_id) do update set (publication_number_full,technology,technology2,technology3)=(excluded.publication_number_full,excluded.technology,excluded.technology2,excluded.technology3)";
            StringJoiner valueJoiner = new StringJoiner(",");
            for(int j = 0; j < i; j++) {
                StringJoiner innerJoiner = new StringJoiner(",","(",")");
                String familyId = familyIds.get(j);
                String pubNum = pubNums.get(j);
                float[] primary = primaryScores.getColumn(j).data().asFloat();
                float[] secondary = secondaryScores.getColumn(j).data().asFloat();
                //System.out.println("Primary: "+primary.length);
                //System.out.println("Secondary: "+secondary.length);
                Pair<Integer,Integer> top = parentChildCombinations.parallelStream().map(p->{
                    return new Pair<>(p,primary[p.getFirst()]+secondary[p.getSecond()]);
                }).sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(1).map(p->p.getFirst()).findFirst().orElse(null);
                if(top!=null) {
                    String tag = allParentsList.get(top.getFirst());
                    String secondaryTag = allChildrenList.get(top.getSecond());
                    Set<String> tertiaryTag = keywordMap.get(familyId);
                    tag = technologyTransformer.apply(tag);
                    secondaryTag = technologyTransformer.apply(secondaryTag);
                    if(tag.equals("MECHANICAL ENGINEERING")&&secondaryTag.equals("METAL HOSE")) {
                        // FRACK
                        System.out.println("FOUND MECHANICAL METAL HOSE: Scores = ("+primary[top.getFirst()]+", "+secondary[top.getSecond()]+")");
                    }
                    innerJoiner.add("'"+familyId+"'").add("'"+pubNum+"'").add("'"+tag+"'").add("'"+secondaryTag+"'");
                    if(tertiaryTag==null||tertiaryTag.isEmpty()) {
                        innerJoiner.add("null");
                    } else {
                        tertiaryTag = tertiaryTag.stream().map(technologyTransformer::apply)
                                .collect(Collectors.toSet());
                        StringJoiner arrayJoiner = new StringJoiner("','","ARRAY['","']");
                        for(String ter : tertiaryTag) {
                            arrayJoiner.add(ter);
                        }
                        innerJoiner.add(arrayJoiner.toString());
                    }
                    valueJoiner.add(innerJoiner.toString());
                    if (firstTech == null) firstTech = tag;
                    if (firstSecondary == null) firstSecondary = secondaryTag;
                    if (firstThird == null) firstThird = tertiaryTag==null ? null : new ArrayList<>(tertiaryTag);
                }
                if (cnt.getAndIncrement() % 10000 == 9999) {
                    System.out.println("Finished: " + cnt.get() + " valid of " + totalCnt.get());
                    System.out.println("Sample "+firstPub+": " + firstTech+"; "+firstSecondary+"; "+firstThird);
                    Database.commit();
                }
            }
            final String statement =  insert.replace("?",valueJoiner.toString());
            System.out.println("Statement: "+statement);
            PreparedStatement insertPs = conn.prepareStatement(statement);
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
        return columnwise?full.getColumns(indices).dup():full.getRows(indices).dup();
    }
}
