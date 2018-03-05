package graphical_modeling.model.nodes;

import graphical_modeling.model.functions.normalization.NormalizationFunction;
import graphical_modeling.util.DoubleDoublePair;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by Evan on 4/13/2017.
 */
public class FactorNode extends Node {
    private static final Random rand = new Random(69);
    @Getter
    protected int[] strides;
    @Getter
    protected int[] cardinalities;
    @Getter
    protected int numVariables;
    @Getter
    protected String[] varLabels;
    @Getter
    @Setter
    protected double[] weights;
    @Getter
    protected Map<String,Integer> varToIndexMap;
    @Getter
    protected int numAssignments;

    public FactorNode(double[] weights, String[] varLabels, int[] cardinalities) {
        super(null,varLabels.length);
        if(varLabels.length!=cardinalities.length) throw new RuntimeException("varLabels and Cardinalities must have same size");
        this.varLabels=varLabels;
        this.cardinalities=cardinalities;
        this.weights=weights;
        this.numVariables=cardinalities.length;
        this.init();
    }


    // If an array is already available
    public FactorNode sumOut(@NonNull String[] toSumOver) {
        Collection<String> Zset = new HashSet<>();
        Arrays.stream(toSumOver).forEach(z->{
            Integer idx = varToIndexMap.get(z);
            if(idx!=null) {
                Zset.add(z);
            }
        });
        List<String> Ys = new ArrayList<>(Arrays.asList(varLabels));
        Zset.forEach(z->Ys.remove(z));

        int num = Ys.size();
        int[] newCardinalities = new int[num];
        String[] newLabels = Ys.toArray(new String[num]);
        for(int i = 0; i < num; i++) {
            String Yi = Ys.get(i);
            int idx = varToIndexMap.get(Yi);
            newCardinalities[i] = cardinalities[idx];
        }
        int newNumAssignments = numAssignmentCombinations(newCardinalities);
        int[] newStridesPrim = computeStrides(newCardinalities);

        // keep indices sorted
        SortedSet<Integer> indicesToSumOver = new TreeSet<>();
        for(String z : Zset) {
            indicesToSumOver.add(varToIndexMap.get(z));
        }

        double[] psi = new double[newNumAssignments];
        Arrays.fill(psi,0d);

        this.assignmentPermutationsStream().parallel().forEach(permutation->{
            int[] assignmentsToKeep = new int[newCardinalities.length];
            int j = 0;
            for(int i = 0; i < cardinalities.length; i++) {
                if(!indicesToSumOver.contains(i)) {
                    assignmentsToKeep[j] = permutation[i];
                    j++;
                }
            }
            int oldIdx = assignmentToIndex(permutation);
            int newIdx = assignmentToIndex(assignmentsToKeep,newStridesPrim);
            double w = weights[oldIdx];
            psi[newIdx]+=w;
        });
        return new FactorNode(psi,newLabels,newCardinalities);
    }

    // returns all possible assignments with given cardinality array
    public Stream<int[]> assignmentPermutationsStream() {
        List<Integer> indices = new ArrayList<>(numAssignments); for(int i = 0; i < numAssignments; i++) indices.add(i);
        return indices.stream().map(idx->{
            int[] assignment = new int[cardinalities.length];
            for(int i = 0; i < cardinalities.length; i++) {
                assignment[i]=indexToAssignment(varLabels[i],idx);
            }
            return assignment;
        });
    }


    public FactorNode multiply(FactorNode other) {
        return applyFunction(other,(pair->pair._1*pair._2));
    }

    public FactorNode divideBy(FactorNode other) {
        return applyFunction(other,(pair->pair._2==0?0:pair._1/pair._2));
    }

    public FactorNode applyFunction(FactorNode other, Function<DoubleDoublePair,Double> f) {
        // Get the union of X1 and X2
        String[] unionLabels = labelUnion(other);
        int unionSize = unionLabels.length;
        int[] unionCardinalities = new int[unionSize];
        int[] myUnionStrides = new int[unionSize];
        int[] otherUnionStrides = new int[unionSize];
        for(int i = 0; i < unionSize; i++) {
            String label = unionLabels[i];
            Integer myIdx = varToIndexMap.get(label);
            Integer otherIdx = other.varToIndexMap.get(label);
            if(myIdx!=null && otherIdx!=null) {
                myUnionStrides[i] = strides[myIdx];
                unionCardinalities[i] = cardinalities[myIdx];
                otherUnionStrides[i] = other.strides[otherIdx];
            } else if(myIdx==null) {
                myUnionStrides[i] = 0;
                unionCardinalities[i] = other.cardinalities[otherIdx];
                otherUnionStrides[i] = other.strides[otherIdx];
            } else if(otherIdx==null) {
                myUnionStrides[i] = strides[myIdx];
                unionCardinalities[i] = cardinalities[myIdx];
                otherUnionStrides[i] = 0;
            }
        }

        // Continue with alg.
        int j = 0;
        int k = 0;
        int[] assignments = new int[unionSize];
        Arrays.fill(assignments,0);

        double[] otherWeights = other.weights;
        int numAssignmentsTotal = numAssignmentCombinations(unionCardinalities);
        double[] psi = new double[numAssignmentsTotal];

        for( int i = 0; i < numAssignmentsTotal; i++) {
            psi[i] = f.apply(new DoubleDoublePair(weights[j],otherWeights[k]));
            for(int l = 0; l < unionSize; l++) {
                assignments[l]++;
                int myStride = myUnionStrides[l];
                int otherStride = otherUnionStrides[l];
                if(assignments[l]==unionCardinalities[l]) {
                    assignments[l]=0;
                    j -= (unionCardinalities[l] - 1)*myStride;
                    k -= (unionCardinalities[l] - 1)*otherStride;
                } else {
                    j += myStride;
                    k += otherStride;
                    break;
                }
            }
        }
        return new FactorNode(psi,unionLabels,unionCardinalities);
    }

    public int nextSample() {
        if(numVariables>1||cardinalities.length<1)  throw new RuntimeException("Can only be a single factor scope");
        double curr = 0d;
        double r = rand.nextDouble();
        for(int i = 0; i < cardinalities[0]; i++) {
            curr+=weights[i];
            if(Double.isNaN(curr)) {
                // pick unif random
                return rand.nextInt(cardinalities[0]);
            }
            if(r <= curr) {
                return i;
            }
        }
        throw new RuntimeException("WARNING: Factor does not appear normalized");
    }

    public void init() {
        if(this.varLabels.length==0) throw new RuntimeException("No var labels");
        this.strides=computeStrides();
        this.varToIndexMap=new HashMap<>();
        this.numAssignments=1;
        for(int i = 0; i < numVariables; i++) {
            varToIndexMap.put(varLabels[i],i);
            numAssignments*=cardinalities[i];
        }
        if(weights!=null && numAssignments!=weights.length) throw new RuntimeException("Invalid factor dimensions");
    }

    public void reNormalize(NormalizationFunction f) {
        f.normalize(weights);
    }


    // where each cardinality number represents a distint variable
    public static int numAssignmentCombinations(int[] cardinalities) {
        int num = 1;
        for(int i = 0; i < cardinalities.length; i++) {
            num*=cardinalities[i];
        }
        return num;
    }


    protected String[] labelUnion(FactorNode other) {
        Set<String> varUnion = new HashSet<>();
        for(String label : varLabels) varUnion.add(label);
        for(String label : other.varLabels) varUnion.add(label);
        String[] unionArray = new String[varUnion.size()];
        return varUnion.toArray(unionArray);
    }

    public int assignmentToIndex(int[] assignments) {
        return assignmentToIndex(assignments,strides);
    }

    public static int assignmentToIndex(int[] assignments, int[] strides) {
        if(assignments.length!=strides.length) throw new RuntimeException("Invalid number of assignments. Should have size: "+strides.length);
        int index = 0;
        for(int i = 0; i < assignments.length; i++) {
            index+= (assignments[i]*strides[i]);
        }
        return index;
    }

    public int indexToAssignment(String varName, int assignmentIdx) {
        Integer varIdx = varToIndexMap.get(varName);
        if(varIdx == null) throw new RuntimeException("Variable "+varName+" not found.");
        int stride = strides[varIdx];
        int cardinality = cardinalities[varIdx];
        return (assignmentIdx/stride) % cardinality;
    }

    public int[] computeStrides() {
        return computeStrides(cardinalities);
    }

    public static int[] computeStrides(int[] cardinalities) {
        int strides[] = new int[cardinalities.length];
        int stride = 1;
        for(int i = 0; i < cardinalities.length; i++) {
            int cardinality = cardinalities[i];
            if(cardinality>0) {
                strides[i] = stride;
                stride = stride * cardinality;
            } else {
                throw new RuntimeException("Stride should be positive");
            }
        }
        return strides;
    }


    @Override
    public String toString() {
        return "Scope: "+Arrays.toString(varLabels)+"\n"+
                "Factor: "+Arrays.toString(weights);
    }
}
