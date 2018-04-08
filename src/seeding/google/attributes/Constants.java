package seeding.google.attributes;

import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Arrays;
import java.util.Collection;

public class Constants {
    public static final String COUNTRY_CODE = "country_code";
    public static final String KIND_CODE = "kind_code";
    public static final String APPLICATION_KIND = "application_kind";
    public static final String APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY = "application_number_formatted";
    public static final String PCT_NUMBER = "pct_number";
    public static final String FAMILY_ID = "family_id";
    public static final String TITLE_LOCALIZED = "title_localized";
    public static final String TEXT = "text";
    public static final String LANGUAGE = "language";
    public static final String CLAIMS_LOCALIZED = "claims_localized";
    public static final String DESCRIPTION_LOCALIZED = "description_localized";
    public static final String PUBLICATION_DATE = "publication_date";
    public static final String FILING_DATE = "filing_date";
    public static final String GRANT_DATE = "grant_date";
    public static final String PRIORITY_DATE = "priority_date";
    public static final String PRIORITY_CLAIM = "priority_claim";
    public static final String NPL_TEXT = "npl_text";
    public static final String CATEGORY = "category";
    public static final String ABSTRACT_LOCALIZED = "abstract_localized";
    public static final String INVENTOR = "inventor";
    public static final String INVENTOR_HARMONIZED = "inventor_harmonized";
    public static final String NAME = "name";
    public static final String ASSIGNEE = "assignee";
    public static final String ASSIGNOR = "assignor";
    public static final String CONVEYANCE_TEXT = "conveyance_text";
    public static final String RECORDED_DATE = "recorded_date";
    public static final String EXECUTION_DATE = "execution_date";
    public static final String REEL_FRAME = "reel_frame";
    public static final String DATE = "date";
    public static final String DOC_NUMBER = "doc_number";
    public static final String IS_FILING = "is_filing";
    public static final String ASSIGNEE_HARMONIZED = "assignee_harmonized";
    public static final String EXAMINER = "examiner";
    public static final String DEPARTMENT = "department";
    public static final String LEVEL = "level";
    public static final String USPC = "uspc";
    public static final String CODE = "code";
    public static final String INVENTIVE = "inventive";
    public static final String FIRST = "first";
    public static final String TREE = "tree";
    public static final String CPC = "cpc";
    public static final String IPC = "ipc";
    public static final String FI = "fi";
    public static final String FTERM = "fterm";
    public static final String CITATION = "citation";
    public static final String TYPE = "type";
    public static final String ENTITY_STATUS = "entity_status";
    public static final String ART_UNIT = "art_unit";
    public static final String FULL_PUBLICATION_NUMBER = "pub_num_full";
    public static final String PUBLICATION_NUMBER_WITH_COUNTRY = "pub_num_country";
    public static final String PUBLICATION_NUMBER = "pub_num";
    public static final String PUBLICATION_NUMBER_GOOGLE = "publication_number";
    public static final String FULL_APPLICATION_NUMBER = "app_num_full";
    public static final String APPLICATION_NUMBER_WITH_COUNTRY = "app_num_country";
    public static final String APPLICATION_NUMBER = "app_num";
    public static final String APPLICATION_NUMBER_GOOGLE = "application_number";
    public static final String APPLICATION_NUMBER_FORMATTED = "app_num_formatted";

    public static Collection<AbstractAttribute> buildAttributes() {
        return Arrays.asList(
                new PublicationNumberWithCountry(),
                new PublicationNumber(),
                new PublicationNumberFull(),
               // new ApplicationNumberWithCountry(),
               // new ApplicationNumber(),
               // new ApplicationNumberFull(),
                new CountryCode(),
                new KindCode(),
               // new ApplicationKind(),
               // new ApplicationNumberFormattedWithCountry(),
               // new ApplicationNumberFormatted(),
               // new PCTNumber(),
                new FamilyId(),
                new TitleLocalized(),
               // new AbstractLocalized(),
               // new ClaimsLocalized(),
               // new DescriptionLocalized(),
                new PublicationDate(),
                new FilingDate(),
                new GrantDate(),
                new PriorityDate(),
               // new PriorityClaim(),
               // new Inventor(),
                new InventorHarmonized(),
               // new Assignee(),
                new AssigneeHarmonized(),
               // new Examiner(),
                //new USPC(),
                //new IPC(),
                new CPC(),
                //new FI(),
                //new Fterm(),
               // new Citation(),
                new EntityStatus(),
                new ArtUnit()
        );
    }
}
