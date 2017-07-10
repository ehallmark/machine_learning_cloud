package ai_db_updater.tools;

import ai_db_updater.iterators.AssignmentIterator;
import ai_db_updater.iterators.PatentGrantIterator;
import ai_db_updater.iterators.url_creators.UrlCreator;

import java.io.File;
import java.time.LocalDate;
import java.time.Month;

/**
 * Created by Evan on 7/5/2017.
 */
public class Constants {
    public static final String DATA_FOLDER = "data/";
    public static final String GOOGLE_URL = "http://storage.googleapis.com/patents/grant_full_text";
    public static final String USPTO_URL = "https://bulkdata.uspto.gov/data2/patent/grant/redbook/fulltext";
    public static final String GOOGLE_APP_URL = "http://storage.googleapis.com/patents/appl_full_text";
    public static final String USPTO_APP_URL = "https://bulkdata.uspto.gov/data2/patent/application/redbook/fulltext";
    public static final UrlCreator GOOGLE_URL_CREATOR = defaultPatentUrlCreator(GOOGLE_URL);
    public static final UrlCreator USPTO_URL_CREATOR = defaultPatentUrlCreator(USPTO_URL);
    public static final UrlCreator GOOGLE_APP_URL_CREATOR = defaultAppUrlCreator(GOOGLE_APP_URL);
    public static final UrlCreator USPTO_APP_URL_CREATOR = defaultAppUrlCreator(USPTO_APP_URL);

    public static final LocalDate DEFAULT_START_DATE = LocalDate.of(2005, Month.JANUARY, 1);
    public static final String DESTINATION_PREFIX = "patent-grant-destinations";
    public static final String PATENT_ZIP_FOLDER = "data/patents/";
    public static final String APP_ZIP_FOLDER = "data/applications/";
    public static final String ASSIGNMENT_ZIP_FOLDER = "data/assignments/";
    public static final PatentGrantIterator DEFAULT_PATENT_GRANT_ITERATOR = new PatentGrantIterator(new File(PATENT_ZIP_FOLDER), DESTINATION_PREFIX);
    public static final PatentGrantIterator DEFAULT_PATENT_APPLICATION_ITERATOR = new PatentGrantIterator(new File(APP_ZIP_FOLDER), DESTINATION_PREFIX);
    public static final AssignmentIterator DEFAULT_ASSIGNMENT_ITERATOR = new AssignmentIterator(new File(ASSIGNMENT_ZIP_FOLDER), DESTINATION_PREFIX);

    private static UrlCreator defaultPatentUrlCreator(String baseUrl) {
        return defaultCreator(baseUrl, "ipg");
    }

    private static UrlCreator defaultAppUrlCreator(String baseUrl) {
        return defaultCreator(baseUrl, "ipa");
    }

    private static UrlCreator defaultCreator(String baseUrl, String prefix) {
        return date -> baseUrl + "/" + date.getYear() + "/" + prefix + date.toString().replace("-", "").substring(2) + ".zip";
    }
}
