package models.assignee.entity_prediction;

import graphical_modeling.util.Pair;
import lombok.Getter;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssigneeGuess {

    private static Pair<String, Double> bestGuessAssignee(String[] inventors, LocalDate filingDate) {
        return null;
    }

    public static void main(String[] args) throws Exception {
        Map<String, List<AssigneeAndDate>> assigneeAndDateMap = new HashMap<>();


    }
}


class AssigneeAndDate {
    @Getter
    private String assignee;
    @Getter
    private LocalDate date;
    public AssigneeAndDate(String assignee, LocalDate date) {
        this.assignee=assignee;
        this.date=date;
    }
}
