package analysis;

import org.nd4j.linalg.api.ndarray.INDArray;
import tools.PatentList;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Created by ehallmark on 9/1/16.
 */
public class GroupedSimilarPatentFinder extends SimilarPatentFinder {
    protected List<SimilarPatentFinder> finders;

    public GroupedSimilarPatentFinder() throws SQLException, IOException, ClassNotFoundException {
        super();
    }

    public GroupedSimilarPatentFinder(List<String> candidateSet, File patentListFile, String name) throws SQLException, IOException, ClassNotFoundException {
        super(candidateSet, patentListFile, name);
    }

    public GroupedSimilarPatentFinder(String name) throws SQLException {
        super(name);
    }

    public GroupedSimilarPatentFinder(List<String> candidateSet, File patentListFile, String name, INDArray eigenVectors) throws SQLException, IOException, ClassNotFoundException {
        super(candidateSet, patentListFile, name, eigenVectors);
    }

    @Override
    public List<Patent> getPatentList() {
        return super.getPatentList();
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public List<PatentList> similarFromCandidateSets(List<SimilarPatentFinder> others, double threshold, int limit, boolean findDissimilar) throws SQLException {
        return super.similarFromCandidateSets(others, threshold, limit, findDissimilar);
    }

    @Override
    public List<PatentList> similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit, boolean findDissimilar) throws SQLException {
        return super.similarFromCandidateSet(other, threshold, limit, findDissimilar);
    }

    @Override
    public List<PatentList> findSimilarPatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit) throws SQLException {
        return super.findSimilarPatentsTo(patentNumber, avgVector, patentNamesToExclude, threshold, limit);
    }

    @Override
    public Double angleBetweenPatents(String name1, String name2) throws SQLException {
        return super.angleBetweenPatents(name1, name2);
    }

    @Override
    public List<PatentList> findOppositePatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit) throws SQLException {
        return super.findOppositePatentsTo(patentNumber, avgVector, patentNamesToExclude, threshold, limit);
    }

    @Override
    public List<PatentList> findOppositePatentsTo(String patentNumber, double threshold, int limit) throws SQLException {
        return super.findOppositePatentsTo(patentNumber, threshold, limit);
    }

    @Override
    public List<PatentList> findSimilarPatentsTo(String patentNumber, double threshold, int limit) throws SQLException {
        return super.findSimilarPatentsTo(patentNumber, threshold, limit);
    }

}
