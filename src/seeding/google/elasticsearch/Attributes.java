package seeding.google.elasticsearch;

import seeding.google.elasticsearch.attributes.*;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Attributes {
    // helper
    public static final String PRIORITY_DATE_ESTIMATED = "priority_date_est";
    public static final String EXPIRED = "expired";
    public static final String EXPIRATION_DATE_ESTIMATED = "expiration_date_est";
    public static final String REMAINING_LIFE = "remaining_life";
    // main
    public static final String PUBLICATION_NUMBER_FULL = "publication_number_full";
    public static final String PUBLICATION_NUMBER = "publication_number";
    public static final String PUBLICATION_NUMBER_WITH_COUNTRY = "publication_number_with_country";
    public static final String APPLICATION_NUMBER_FULL = "application_number_full";
    public static final String APPLICATION_NUMBER = "application_number";
    public static final String APPLICATION_NUMBER_WITH_COUNTRY = "application_number_with_country";
    public static final String APPLICATION_NUMBER_FORMATTED = "application_number_formatted";
    public static final String APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY = "application_number_formatted_with_country";
    public static final String FILING_DATE = "filing_date";
    public static final String PUBLICATION_DATE = "publication_date";
    public static final String PRIORITY_DATE = "priority_date";
    public static final String COUNTRY_CODE = "country_code";
    public static final String KIND_CODE = "kind_code";
    public static final String APPLICATION_KIND = "application_kind";
    public static final String FAMILY_ID = "family_id";
    public static final String ORIGINAL_ENTITY_TYPE = "original_entity_type";
    public static final String INVENTION_TITLE = "invention_title";
    public static final String ABSTRACT = "abstract";
    public static final String CLAIMS = "claims";
    public static final String DESCRIPTION = "description";
    public static final String INVENTORS = "inventors";
    public static final String INVENTOR_HARMONIZED = "inventor_harmonized";
    public static final String INVENTOR_HARMONIZED_CC = "inventor_harmonized_cc";
    public static final String ASSIGNEES = "assignees";
    public static final String ASSIGNEE_HARMONIZED = "assignee_harmonized";
    public static final String ASSIGNEE_HARMONIZED_CC = "assignee_harmonized_cc";
    public static final String PRIORITY_CLAIMS = "priority_claims";
    public static final String PC_PUBLICATION_NUMBER_FULL = "pc_publication_number_full";
    public static final String PC_APPLICATION_NUMBER_FULL = "pc_application_number_full";
    public static final String PC_FILING_DATE = "pc_filing_date";
    public static final String CODE = "code";
    public static final String TREE = "tree";
    public static final String CITATIONS = "citations";
    public static final String CITED_PUBLICATION_NUMBER_FULL = "cited_publication_number_full";
    public static final String CITED_APPLICATION_NUMBER_FULL = "cited_application_number_full";
    public static final String CITED_NPL_TEXT = "cited_npl_text";
    public static final String CITED_TYPE = "cited_type";
    public static final String CITED_CATEGORY = "cited_category";
    public static final String CITED_FILING_DATE = "cited_filing_date";
    public static final String AI_VALUE = "ai_value";
    public static final String LENGTH_OF_SMALLEST_IND_CLAIM = "length_of_smallest_ind_claim";
    public static final String MEANS_PRESENT = "means_present";
    public static final String FAMILY_SIZE = "family_size";
    public static final String SSO = "sso";
    public static final String STANDARD = "standard";
    public static final String STANDARDS = "standards";
    public static final String WIPO_TECHNOLOGY = "wipo_technology";
    public static final String TECHNOLOGY = "technology";
    public static final String TECHNOLOGY2 = "technology2";
    public static final String MAINTENANCE_EVENT = "maintenance_event";
    public static final String LAPSED = "lapsed";
    public static final String REINSTATED = "reinstated";
    public static final String LATEST_ASSIGNEES = "latest_assignees";
    public static final String LATEST_ASSIGNEE = "latest_assignee";
    public static final String LATEST_ASSIGNEE_DATE = "latest_assignee_date";
    public static final String LATEST_SECURITY_INTEREST = "latest_security_interest";
    public static final String LATEST_FIRST_ASSIGNEE = "latest_first_assignee";
    public static final String LATEST_PORTFOLIO_SIZE = "latest_portfolio_size";
    public static final String LATEST_ENTITY_TYPE = "latest_entity_type";
    public static final String LATEST_FIRST_FILING_DATE = "latest_first_filing_date";
    public static final String LATEST_LAST_FILING_DATE = "latest_last_filing_date";
    public static final String LATEST_FAM_ASSIGNEES = "latest_fam_assignees";
    public static final String LATEST_FAM_ASSIGNEE = "latest_fam_assignee";
    public static final String LATEST_FAM_ASSIGNEE_DATE = "latest_fam_assignee_date";
    public static final String LATEST_FAM_SECURITY_INTEREST = "latest_fam_security_interest";
    public static final String LATEST_FAM_FIRST_ASSIGNEE = "latest_fam_first_assignee";
    public static final String LATEST_FAM_PORTFOLIO_SIZE = "latest_fam_portfolio_size";
    public static final String LATEST_FAM_ENTITY_TYPE = "latest_fam_entity_type";
    public static final String LATEST_FAM_FIRST_FILING_DATE = "latest_fam_first_filing_date";
    public static final String LATEST_FAM_LAST_FILING_DATE = "latest_fam_last_filing_date";
    public static final String CPC_VAE = "cpc_vae";
    public static final String RNN_ENC = "rnn_enc";
    public static final String ASSIGNMENTS = "assignments";
    public static final String REEL_FRAME = "reel_frame";
    public static final String CONVEYANCE_TEXT = "conveyance_text";
    public static final String EXECUTION_DATE = "execution_date";
    public static final String RECORDED_DATE = "recorded_date";
    public static final String RECORDED_ASSIGNEE = "recorded_assignee";
    public static final String RECORDED_ASSIGNOR = "recorded_assignor";
    public static final String RCITATIONS = "rcitations";
    public static final String RCITE_PUBLICATION_NUMBER_FULL = "rcite_publication_number_full";
    public static final String RCITE_APPLICATION_NUMBER_FULL = "rcite_application_number_full";
    public static final String RCITE_FAMILY_ID = "rcite_family_id";
    public static final String RCITE_TYPE = "rcite_type";
    public static final String RCITE_CATEGORY = "rcite_category";
    public static final String RCITE_FILING_DATE = "rcite_filing_date";
    public static final String TERM_ADJUSTMENTS = "term_adjustments";
    public static final String COMPDB = "compdb";
    public static final String COMPDB_DEAL_ID = "compdb_deal_id";
    public static final String COMPDB_RECORDED_DATE = "compdb_recorded_date";
    public static final String COMPDB_TECHNOLOGY = "compdb_technology";
    public static final String COMPDB_INACTIVE = "compdb_inactive";
    public static final String COMPDB_ACQUISITION = "compdb_acquisition";
    public static final String GATHER = "gather";
    public static final String GATHER_VALUE = "gather_value";
    public static final String GATHER_STAGE = "gather_stage";
    public static final String GATHER_TECHNOLOGY = "gather_technology";
    public static final String PTAB = "ptab";
    public static final String PTAB_APPEAL_NO = "ptab_appeal_no";
    public static final String PTAB_INTERFERENCE_NO = "ptab_interference_no";
    public static final String PTAB_MAILED_DATE = "ptab_mailed_date";
    public static final String PTAB_INVENTOR_LAST_NAME = "ptab_inventor_last_name";
    public static final String PTAB_INVENTOR_FIRST_NAME = "ptab_inventor_first_name";
    public static final String PTAB_CASE_NAME = "ptab_case_name";
    public static final String PTAB_CASE_TYPE = "ptab_case_type";
    public static final String PTAB_CASE_STATUS = "ptab_case_status";
    public static final String PTAB_CASE_TEXT = "ptab_case_text";
    public static Collection<AbstractAttribute> buildAttributes() {
        return Arrays.asList(
                new PublicationNumberFull(),
                new PublicationNumber(),
                new PublicationNumberWithCountry(),
                new ApplicationNumberFull(),
                new ApplicationNumber(),
                new ApplicationNumberWithCountry(),
                new ApplicationNumberFormatted(),
                new ApplicationNumberFormattedWithCountry(),
                new FilingDate(),
                new PublicationDate(),
                new PriorityDate(),
                new CalculatedPriorityDate(),
                new CalculatedExpirationDate(),
                new RemainingLife(),
                new Expired(),
                new CountryCode(),
                new KindCode(),
                new ApplicationKind(),
                new FamilyID(),
                new OriginalEntityType(),
                new InventionTitle(),
                new Abstract(),
                new Claims(),
                new Description(),
                new Inventors(),
                new Assignees(),
                new PriorityClaims(),
                new Code(),
                new Tree(),
                new Citations(),
                new AIValue(),
                new LengthOfSmallestIndClaim(),
                new MeansPresent(),
                new FamilySize(),
                new Standards(),
                new WipoTechnology(),
                new GttTechnology(),
                new GttTechnology2(),
                new MaintenanceEvent(),
                new Lapsed(),
                new Reinstated(),
                new LatestAssignees(),
                new LatestFamAssignees(),
                new CPCVae(),
                new RnnEnc(),
                new Assignments(),
                new RCitations(),
                new TermAdjustments(),
                new CompDB(),
                new Gather(),
                new PTAB()
        );
    }

    public static Map<String,NestedAttribute> getNestedAttrMap() {
        Map<String,NestedAttribute> nestedAttrMap = new HashMap<>();
        buildAttributes().forEach(attr->{
            if(attr instanceof NestedAttribute) {
                nestedAttrMap.put(attr.getName(),(NestedAttribute)attr);
            }
        });
        return nestedAttrMap;
    }
}
