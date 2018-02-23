package models.similarity_models.combined_similarity_model;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestModelHelper {
    protected static int intersect(Collection<String> c1, Collection<String> c2) {
        Set<String> s = new HashSet<>(c1);
        s.removeAll(c2);
        return s.size();
    }

    protected static int union(Collection<String> c1, Collection<String> c2) {
        Set<String> s = new HashSet<>(c1);
        s.addAll(c2);
        return s.size();
    }

    protected static Set<String> topNByCosineSim(List<String> filings, INDArray filingMatrix, INDArray encodingVec, int n) {
        float[] results = filingMatrix.mmul(Transforms.unitVec(encodingVec).reshape(encodingVec.length(),1)).data().asFloat();
        if(filings.size()!=results.length) {
            throw new IllegalStateException("Filings size ("+filings.size()+") is not equal to results length ("+results.length+")");
        }
        return IntStream.range(0,results.length).mapToObj(i->new Pair<>(filings.get(i),results[i]))
                .sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond()))
                .limit(n)
                .map(p->p.getFirst())
                .collect(Collectors.toSet());
    }

    protected static Pair<List<String>,INDArray> createFilingMatrix(Map<String,INDArray> filingToVectorMap) {
        int columns = filingToVectorMap.values().stream().findAny().get().length();
        INDArray mat = Nd4j.create(filingToVectorMap.size(),columns);
        List<String> filings = Collections.synchronizedList(new ArrayList<>(filingToVectorMap.keySet()));
        for(int i = 0; i < filings.size(); i++) {
            mat.putRow(i, Transforms.unitVec(filingToVectorMap.get(filings.get(i))));
        }
        return new Pair<>(filings,mat);
    }
}
