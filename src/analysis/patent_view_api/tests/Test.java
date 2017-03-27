package analysis.patent_view_api.tests;

import analysis.patent_view_api.PatentAPIHandler;
import com.google.gson.Gson;

import java.time.LocalDate;

/**
 * Created by ehallmark on 3/8/17.
 */
public class Test {
    public static void main(String[] args) {
        LocalDate month = LocalDate.now().minusYears(2);
        System.out.println(String.join(" ",new Gson().toJson(PatentAPIHandler.requestMonthlyPatents(month))));
    }
}
