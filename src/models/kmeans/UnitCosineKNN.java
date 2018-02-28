package models.kmeans;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class UnitCosineKNN<T> {

    private List<T> items;
    private Map<T,Integer> itemToIdxMap;
    private INDArray matrix;
    private INDArray resultMatrix;
    public UnitCosineKNN(List<T> items, INDArray matrix) {
        this.matrix=matrix;
        this.items=items;
        this.itemToIdxMap = createIdxMap(items);
        this.matrix.diviColumnVector(this.matrix.norm2(1));
    }

    public UnitCosineKNN(Map<T,INDArray> dataMap) {
        this.items = Collections.synchronizedList(new ArrayList<>(dataMap.keySet()));
        this.itemToIdxMap = createIdxMap(items);
        this.matrix = Nd4j.create(items.size(),dataMap.values().stream().findAny().get().length());
        for(int i = 0; i < items.size(); i++) {
            matrix.putRow(i,dataMap.get(items.get(i)));
        }
        this.matrix.diviColumnVector(this.matrix.norm2(1));
    }

    private Map<T,Integer> createIdxMap(List<T> items) {
        return IntStream.range(0,items.size()).mapToObj(i->new Pair<>(items.get(i),i))
                .collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond()));
    }

    public void init() {
        if(this.resultMatrix==null) {
            System.out.println("Computing result matrix...");
            this.resultMatrix = MatrixMultiplication.multiplyInBatches(matrix,matrix.transpose(),1024);
            Nd4j.doAlongDiagonal(this.resultMatrix,n->-1d);
            System.out.println("Finished.");
        }
    }

    public Map<T,T> allItemsToNearestNeighbor() {
        Map<T,T> data = Collections.synchronizedMap(new HashMap<>(items.size()));
        INDArray indices = Nd4j.argMax(resultMatrix,0);
        int[] indexArray = indices.data().asInt();
        IntStream.range(0,indexArray.length).forEach(i->{
            data.put(items.get(i),items.get(indexArray[i]));
        });
        return data;
    }

    public List<T> kNearestNeighborsOf(T item, int k) {
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

    public List<T> similarNeighborsOf(T item, double threshold) {
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
