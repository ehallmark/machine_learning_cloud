package seeding.patent_view_api.tests;

import seeding.patent_view_api.PatentAPIHandler;

import java.time.LocalDate;

/**
 * Created by ehallmark on 3/8/17.
 */
public class Test {
    public static void main(String[] args) {
        LocalDate month = LocalDate.now().minusYears(2);
        System.out.println(String.join("\n",PatentAPIHandler.requestPatentNumbersFromAssignee("Unwired Planet")));
    }
}
