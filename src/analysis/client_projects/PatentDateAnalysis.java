package analysis.client_projects;

import analysis.patent_view_api.Patent;
import analysis.patent_view_api.PatentAPIHandler;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.time.LocalDate;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/11/2017.
 */
public class PatentDateAnalysis {

    private static Collection<String> filterLateInTheGamePatents(Collection<String> patents, double deviations) {
        Collection<Patent> patentList = PatentAPIHandler.requestAllPatents(patents).stream()
                .filter(p->p!=null&&p.getPubDate()!=null).collect(Collectors.toList());
        // collect date statistics

        double mean;
        double stddev;
        INDArray dateData = Nd4j.create(patentList.size());
        int i = 0;
        for(Patent patent : patentList) {
            dateData.putScalar(i,getTrend(patent.getPubDate()));
            i++;
        }
        mean=dateData.meanNumber().doubleValue();
        stddev=Math.sqrt(dateData.varNumber().doubleValue());
        return patentList.stream() // shift trend to promote early invention and prevent bias
                .filter(patent->(Math.abs((getTrend(patent.getPubDate())+(stddev/2.0)-mean))/stddev)<deviations)
                .map(patent->patent.getPatentNumber())
                .collect(Collectors.toList());
    }

    public static double getTrend(LocalDate date) {
        return (date.getYear())+(date.getMonthValue()-1.0)/12.0;
    }

    public static void main(String[] args) throws Exception {
        final double deviations = 1.5d;
        Collection<String> patents2G = filterLateInTheGamePatents(MicrosoftSEPProject.loadKeywordFile(new File("relevant_patents_2g_output.csv")),deviations);
        MicrosoftSEPProject.writeListToFile(patents2G,new File("patents_2g_date_filter.csv"));
        System.out.println("Patents : "+String.join("; ",patents2G));

        Collection<String> patents3G = filterLateInTheGamePatents(MicrosoftSEPProject.loadKeywordFile(new File("relevant_patents_3g_output.csv")),deviations);
        MicrosoftSEPProject.writeListToFile(patents3G,new File("patents_3g_date_filter.csv"));
        System.out.println("Patents : "+String.join("; ",patents3G));

        Collection<String> patents4G = filterLateInTheGamePatents(MicrosoftSEPProject.loadKeywordFile(new File("relevant_patents_4g_output.csv")),deviations);
        MicrosoftSEPProject.writeListToFile(patents4G,new File("patents_4g_date_filter.csv"));
        System.out.println("Patents : "+String.join("; ",patents4G));

    }
}
