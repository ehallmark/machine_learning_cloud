package analysis;

import j2html.tags.Tag;
import server.tools.AbstractPatent;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

/**
 * Created by ehallmark on 9/11/16.
 */
public class Classification implements Comparable<Classification> {
    private AbstractPatent patent;
    private String[] scores;
    private String[] classHierarchy;
    private List<Double> myScores;
    Classification(Patent patent, String[] scores, String[] classHierarchy) throws SQLException {
        this.patent= Patent.abstractClone(patent,"");
        this.scores=scores;
        this.classHierarchy=classHierarchy;
        myScores = Arrays.asList(scores).stream().map(str->str==null||str.trim().length()==0?0.0:Arrays.stream(str.split("\\|")).collect(Collectors.summingDouble(s->Float.valueOf(s)))).collect(Collectors.toList());

    }

    public static Tag getTable(int depth, List<Tag> tableRows) {
        int[] classNums = new int[depth];
        for(int i = 0; i < depth; i++) {
            classNums[i]=i+1;
        }
        List<Tag> headers = new ArrayList<>(depth*2+2);
        headers.add(th("Patent #"));
        headers.add(th("Title"));
        Arrays.stream(classNums).forEach(i-> {
            headers.add(th("Score "+i));
            headers.add(th("Class " + i));
        });

        return table().with(
          thead().with(
                  tr().with(
                          headers
                  )
          ),tbody().with(
                  tableRows
                )
        );
    }

    public Tag toTableRow() {
        List<Tag> data = new ArrayList<>(scores.length*2+2);
        data.add(td(patent.getName()));
        data.add(td(patent.getTitle()));
        for(int i = 0; i < scores.length; i++) {
            data.add(td(scores[i]));
            data.add(td(classHierarchy[i]));
        }
        return tr().with(
            data
        );
    }

    @Override
    public int compareTo(Classification o) {
        for(int i = 0; i < myScores.size(); i++){
            int toReturn = Double.compare(myScores.get(i),o.myScores.get(i));
            if(toReturn!=0) return toReturn;
        }
        return 0;
    }
}