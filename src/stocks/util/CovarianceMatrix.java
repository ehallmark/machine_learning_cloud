package stocks.util;

import lombok.Getter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.util.MathUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CovarianceMatrix {
    private Map<String,List<Pair<LocalDate, Double>>> data;
    @Getter
    private List<String> categories;
    @Getter
    private INDArray covMatrix;
    private int n;
    public CovarianceMatrix(Map<String,List<Pair<LocalDate, Double>>> data, int n) {
        this.data=data;
        this.n=n;
        init();
    }

    private void init() {
        this.categories = Collections.synchronizedList(new ArrayList<>(data.keySet()));
        INDArray A = Nd4j.create(n,categories.size());
        for(int i = 0; i < categories.size(); i++) {
            A.putColumn(i,Nd4j.create(data.get(categories.get(i)).stream().mapToDouble(p->p.getSecond()).toArray()));
        }
        INDArray one = Nd4j.ones(n,1);
        INDArray a = A.sub(one.mmul(one.transpose()).mmul(A).divi(n));
        covMatrix = a.mmul(a.transpose()).divi(n);
        INDArray std = A.std(true,0);
        covMatrix.diviColumnVector(std).diviRowVector(std);


        System.out.println("Correl: "+covMatrix.toString());
    }
}
