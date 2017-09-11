package models.keyphrase_prediction;

import lombok.NonNull;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.*;

/**
 * Created by Evan on 9/11/2017.
 */
public class MultiStem {
    private static final Map<Integer,Double> rowSums = Collections.synchronizedMap(new HashMap<>());
    protected String[] stems;
    protected int index;
    private int length;
    private Double unitHood;
    private Double termHood;

    public MultiStem(@NonNull String[] stems, @NonNull int index) {
        this.stems=stems;
        this.index=index;
        this.length=stems.length;
    }

    public double computeUnithoodScore(double frequency) {
        if(unitHood==null) {
            unitHood = frequency * Math.log(length + 1);
        }
        return unitHood;
    }

    public double computeTermhoodScore(INDArray M, Collection<MultiStem> allStems) {
        if(termHood==null) {
            Double rowSumi = rowSums.getOrDefault(this.index, sumAcrossSingleRow(M, this.index));
            rowSums.putIfAbsent(this.index, rowSumi);

            termHood = allStems.stream().mapToDouble(stem -> {
                int stemIdx = stem.index;
                if (stemIdx != this.index) {
                    // compute sum of Mjk
                    Double rowSumj = rowSums.get(stemIdx);
                    if (rowSumj == null) {
                        rowSumj = sumAcrossSingleRow(M, stemIdx);
                        rowSums.put(stem.index, rowSumj);
                    }
                    double Mij = M.getDouble(this.index, stem.index);
                    double sumMiksumMjk = rowSumi * rowSumj;
                    return Math.pow(Mij - sumMiksumMjk, 2) / sumMiksumMjk;
                } else {
                    return 0d;
                }

            }).sum();
        }
        return termHood;
    }

    private double sumAcrossSingleRow(INDArray M, int idx) {
        return M.getRow(idx).sumNumber().doubleValue();
    }

    @Override
    public boolean equals(Object other) {
        if(!(other instanceof MultiStem)) return false;
        return Arrays.deepEquals(((MultiStem) other).stems, stems);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(stems);
    }
}
