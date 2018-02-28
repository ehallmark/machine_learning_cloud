package models.kmeans;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class UnitCosineKNN {

    private List<String> items;
    private Map<String,Integer> itemToIdxMap;
    private INDArray matrix;
    private INDArray resultMatrix;
    public UnitCosineKNN(List<String> items, INDArray matrix) {
        this.matrix=matrix;
        this.items=items;
        this.itemToIdxMap = createIdxMap(items);
        this.matrix.diviColumnVector(this.matrix.norm2(1));
    }

    public UnitCosineKNN(Map<String,INDArray> dataMap) {
        this.items = Collections.synchronizedList(new ArrayList<>(dataMap.keySet()));
        this.itemToIdxMap = createIdxMap(items);
        this.matrix = Nd4j.create(items.size(),dataMap.values().stream().findAny().get().length());
        for(int i = 0; i < items.size(); i++) {
            matrix.putRow(i,dataMap.get(items.get(i)));
        }
        this.matrix.diviColumnVector(this.matrix.norm2(1));
    }

    private static Map<String,Integer> createIdxMap(List<String> items) {
        return IntStream.range(0,items.size()).mapToObj(i->new Pair<>(items.get(i),i))
                .collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond()));
    }

    public void init() {
        if(this.resultMatrix==null) {
            System.out.println("Computing result matrix...");
            this.resultMatrix = MatrixMultiplication.multiplyInBatches(matrix,matrix.transpose(),1024);
            System.out.println("Finished.");
        }
    }

    public List<String> kNearestNeighborsOf(String item, int k) {
        if(resultMatrix==null) throw new RuntimeException("Must init kNN");
        int idx = itemToIdxMap.getOrDefault(item,-1);
        if(idx<0) return Collections.emptyList();
        INDArray res = resultMatrix.getRow(idx);
        float[] scores = res.data().asFloat();
        return IntStream.range(0,scores.length).mapToObj(i->{
            if(i==idx) return null;
            return new Pair<>(i,scores[i]);
        }).filter(obj->obj!=null).sorted((e1,e2)->e2.getSecond().compareTo(e1.getSecond()))
                .limit(k)
                .map(e->items.get(e.getFirst().intValue()))
                .collect(Collectors.toList());
    }

    public List<String> similarNeighborsOf(String item, double threshold) {
        if(resultMatrix==null) throw new RuntimeException("Must init kNN");
        int idx = itemToIdxMap.getOrDefault(item,-1);
        if(idx<0) return Collections.emptyList();
        INDArray res = resultMatrix.getRow(idx);
        float[] scores = res.data().asFloat();
        return IntStream.range(0,scores.length).mapToObj(i->{
            if(i==idx) return null;
            return new Pair<>(i,scores[i]);
        }).filter(obj->obj!=null&&obj.getSecond()>threshold).sorted((e1,e2)->e2.getSecond().compareTo(e1.getSecond()))
                .map(e->items.get(e.getFirst().intValue()))
                .collect(Collectors.toList());
    }

}
