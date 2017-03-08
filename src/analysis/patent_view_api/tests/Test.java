package analysis.patent_view_api.tests;

import analysis.patent_view_api.PatentAPIHandler;

/**
 * Created by ehallmark on 3/8/17.
 */
public class Test {
    public static void main(String[] args) {
        String assignee = "vivint";
        System.out.println(String.join(" ",PatentAPIHandler.requestPatentNumbersFromAssignee(assignee)));
    }
}
