package ui_models.attributes.value;

import seeding.Database;
import ui_models.attributes.ValueAttr;

import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class PortfolioSizeEvaluator extends ValueAttr {

    public PortfolioSizeEvaluator() {
        this("Portfolio Size Value");
    }

    protected PortfolioSizeEvaluator(String name) {
        super(ValueMapNormalizer.DistributionType.Normal,name);
        setModel();
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList(runModel());
    }

    private static Map<String,Double> runModel(){
        Collection<String> assignees = Database.getAssignees();
        Map<String,Double> map = new HashMap<>(assignees.size());
        assignees.forEach(assignee->{
            map.put(assignee,new Double(Database.getExactAssetCountFor(assignee)));
        });
        return map;
    }
}
