package seeding.google.elasticsearch;

import org.nd4j.linalg.primitives.Pair;
import seeding.google.elasticsearch.attributes.*;
import user_interface.acclaim_compatibility.GlobalParser;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Attributes {
    // count
    public static final String GRANTED = "granted";
    public static final String SIMILARITY = "similarity";
    public static final String CITATIONS_COUNT = "citation_count";
    public static final String RCITATIONS_COUNT = "rcite_count";
    public static final String CODE_COUNT = "code_count";
    public static final String PRIORITY_CLAIMS_COUNT = "pc_count";
    public static final String INVENTORS_COUNT = "inventor_count";
    public static final String ASSIGNEES_COUNT = "assignee_count";
    public static final String ASSIGNMENTS_COUNT = "assignment_count";
    public static final String CLAIMS_COUNT = "claim_count";
    public static final String KEYWORD_COUNT = "keyword_count";
    public static final String MAINTENANCE_EVENT_COUNT = "maintenance_event_count";
    public static final String LATEST_ASSIGNEE_COUNT = "latest_assignee_count";
    public static final String LATEST_FAM_ASSIGNEE_COUNT = "latest_fam_assignee_count";
    public static final String COMPDB_COUNT = "compdb_count";
    public static final String STANDARD_COUNT = "standard_count";
    public static final String PTAB_COUNT = "ptab_count";
    public static final String SECURITY_INTEREST_FAM = "security_interest_fam";
    public static final String SECURITY_INTEREST = "security_interest";
    // helper
    public static final String PRIORITY_DATE_ESTIMATED = "priority_date_est";
    public static final String EXPIRED = "expired";
    public static final String EXPIRATION_DATE_ESTIMATED = "expiration_date_est";
    public static final String REMAINING_LIFE = "remaining_life";
    // main
    public static final String KEYWORDS = "keywords";
    public static final String PUBLICATION_NUMBER_FULL = "publication_number_full";
    public static final String PUBLICATION_NUMBER = "publication_number";
    public static final String PUBLICATION_NUMBER_WITH_COUNTRY = "publication_number_with_country";
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
    public static final String PC_PUBLICATION_NUMBER = "pc_publication_number";
    public static final String PC_PUBLICATION_NUMBER_WITH_COUNTRY = "pc_publication_number_with_country";
    public static final String PC_APPLICATION_NUMBER_FORMATTED = "pc_application_number_formatted";
    public static final String PC_APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY = "pc_application_number_formatted_with_country";
    public static final String PC_FILING_DATE = "pc_filing_date";
    public static final String PC_FAMILY_ID = "pc_family_id";
    public static final String CODE = "code";
    public static final String TREE = "tree";
    public static final String CITATIONS = "citations";
    public static final String CITED_PUBLICATION_NUMBER_FULL = "cited_publication_number_full";
    public static final String CITED_PUBLICATION_NUMBER = "cited_publication_number";
    public static final String CITED_PUBLICATION_NUMBER_WITH_COUNTRY = "cited_publication_number_with_country";
    public static final String CITED_FAMILY_ID = "cited_family_id";
    public static final String CITED_APPLICATION_NUMBER_FORMATTED = "cited_application_number_formatted";
    public static final String CITED_APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY = "cited_application_number_formatted_with_country";
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
    public static final String LATEST_FIRST_ASSIGNEE = "latest_first_assignee";
    public static final String LATEST_PORTFOLIO_SIZE = "latest_portfolio_size";
    public static final String LATEST_ENTITY_TYPE = "latest_entity_type";
    public static final String LATEST_FIRST_FILING_DATE = "latest_first_filing_date";
    public static final String LATEST_LAST_FILING_DATE = "latest_last_filing_date";
    public static final String LATEST_FAM_ASSIGNEES = "latest_fam_assignees";
    public static final String LATEST_FAM_ASSIGNEE = "latest_fam_assignee";
    public static final String LATEST_FAM_ASSIGNEE_DATE = "latest_fam_assignee_date";
    public static final String SECURITY_INTEREST_FAM_HOLDER = "security_interest_fam_holder";
    public static final String SECURITY_INTEREST_FAM_DATE = "security_interest_fam_date";
    public static final String SECURITY_INTEREST_HOLDER = "security_interest_holder";
    public static final String SECURITY_INTEREST_DATE = "security_interest_date";
    public static final String LATEST_FAM_FIRST_ASSIGNEE = "latest_fam_first_assignee";
    public static final String LATEST_FAM_PORTFOLIO_SIZE = "latest_fam_portfolio_size";
    public static final String LATEST_FAM_ENTITY_TYPE = "latest_fam_entity_type";
    public static final String LATEST_FAM_FIRST_FILING_DATE = "latest_fam_first_filing_date";
    public static final String LATEST_FAM_LAST_FILING_DATE = "latest_fam_last_filing_date";
    public static final String ENC = "enc";
    public static final String ASSIGNMENTS = "assignments";
    public static final String REEL_FRAME = "reel_frame";
    public static final String CONVEYANCE_TEXT = "conveyance_text";
    public static final String EXECUTION_DATE = "execution_date";
    public static final String RECORDED_DATE = "recorded_date";
    public static final String RECORDED_ASSIGNEE = "recorded_assignee";
    public static final String RECORDED_ASSIGNOR = "recorded_assignor";
    public static final String RCITATIONS = "rcitations";
    public static final String RCITE_PUBLICATION_NUMBER_FULL = "rcite_publication_number_full";
    public static final String RCITE_PUBLICATION_NUMBER = "rcite_publication_number";
    public static final String RCITE_PUBLICATION_NUMBER_WITH_COUNTRY = "rcite_publication_number_with_country";
    public static final String RCITE_APPLICATION_NUMBER_FORMATTED = "rcite_application_number_formatted";
    public static final String RCITE_APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY = "rcite_application_number_formatted_with_country";
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
                //new Description(), :(
                new Inventors(),
                new InventorCount(),
                new Assignees(),
                new AssigneeCount(),
                new PriorityClaims(),
                new PriorityClaimCount(),
                new Code(),
                new CodeCount(),
                new Tree(),
                new Citations(),
                new CitationCount(),
                new AIValue(),
                new LengthOfSmallestIndClaim(),
                new ClaimCount(),
                new MeansPresent(),
                new FamilySize(),
                new Standards(),
                new StandardCount(),
                new WipoTechnology(),
                new GttTechnology(),
                new GttTechnology2(),
                new GttKeywords(),
                new KeywordCount(),
                new MaintenanceEvent(),
                new MaintenanceEventCount(),
                new Lapsed(),
                new Reinstated(),
                new LatestAssignees(),
                new LatestAssigneeCount(),
                new LatestFamAssignees(),
                new LatestFamAssigneeCount(),
                new Enc(),
                new Assignments(),
                new AssignmentCount(),
                new RCitations(),
                new RCitationCount(),
                new TermAdjustments(),
                new CompDB(),
                new CompDBCount(),
                new Gather(),
                new PTAB(),
                new PTABCount(),
                new Granted(),
                new SecurityInterest(),
                new SecurityInterestFam()
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


    public static final Map<String,String> ACCLAIM_IP_TO_ATTR_NAME_MAP = Stream.of(
            Arrays.asList("ANG",LATEST_FAM_ASSIGNEES+"."+LATEST_FAM_ASSIGNEE),
            Arrays.asList("ANG_F",LATEST_FAM_ASSIGNEES+"."+LATEST_FAM_FIRST_ASSIGNEE),
            Arrays.asList("ANC",LATEST_ASSIGNEES+"."+LATEST_ASSIGNEE),
            Arrays.asList("ANC_F",LATEST_ASSIGNEES+"."+LATEST_FIRST_ASSIGNEE),
            Arrays.asList("ANO",ASSIGNEES+"."+ASSIGNEE_HARMONIZED),
            Arrays.asList("AN_ORIG",ASSIGNEES+"."+ASSIGNEE_HARMONIZED),
            Arrays.asList("ACN", ASSIGNEES+"."+ASSIGNEE_HARMONIZED_CC),
            Arrays.asList("PRIRD", PRIORITY_DATE),
            Arrays.asList("APD", FILING_DATE),
            Arrays.asList("ISD", PUBLICATION_DATE),
            Arrays.asList("PD", PUBLICATION_DATE),
            Arrays.asList("EXP", EXPIRATION_DATE_ESTIMATED),
            Arrays.asList("APN", APPLICATION_NUMBER_FORMATTED),
            Arrays.asList("GPN", PUBLICATION_NUMBER),
            Arrays.asList("PN", PUBLICATION_NUMBER),
            Arrays.asList("CC", COUNTRY_CODE),
            Arrays.asList("SMALLENT", ORIGINAL_ENTITY_TYPE),
            Arrays.asList("CPC", CODE),
            Arrays.asList("IN", INVENTORS+"."+INVENTOR_HARMONIZED),
            Arrays.asList("ICN", INVENTORS+"."+INVENTOR_HARMONIZED_CC),
            Arrays.asList("FCITE", CITATIONS+"."+CITED_PUBLICATION_NUMBER),
            Arrays.asList("RCITE", RCITATIONS+"."+RCITE_PUBLICATION_NUMBER),
            Arrays.asList("FCITE_CT", CITATIONS_COUNT),
            Arrays.asList("RCITE_CT", RCITATIONS_COUNT),
            Arrays.asList("ANA_SFAM_CT", PRIORITY_CLAIMS_COUNT),
            Arrays.asList("ANA_INV_CT", INVENTORS_COUNT),
            Arrays.asList("ANA_AS_CT", ASSIGNEES_COUNT),
            Arrays.asList("ANA_CLM_CT", CLAIMS_COUNT),
            Arrays.asList("KCOD", KIND_CODE),
            Arrays.asList("ANA_EXT_DAYS", TERM_ADJUSTMENTS),
            Arrays.asList("ANA_FCLM_TW", LENGTH_OF_SMALLEST_IND_CLAIM),
            Arrays.asList("ANA_CLM_TW", LENGTH_OF_SMALLEST_IND_CLAIM),
            Arrays.asList("ANA_ANRE_EXE_CT",ASSIGNMENTS_COUNT),
            Arrays.asList("REC_DT", ASSIGNMENTS+"."+RECORDED_DATE),
            Arrays.asList("EXE_DT", ASSIGNMENTS+"."+EXECUTION_DATE),
            Arrays.asList("ANRE_EXE_DT", LATEST_ASSIGNEE+"."+EXECUTION_DATE),
            Arrays.asList("REFN",PUBLICATION_NUMBER),
            Arrays.asList("REF",PUBLICATION_NUMBER),
            Arrays.asList("EVENTCODE",MAINTENANCE_EVENT),
            Arrays.asList("RVI", AI_VALUE),
            Arrays.asList("TTL", INVENTION_TITLE),
            Arrays.asList("ABST", ABSTRACT),
            Arrays.asList("ACLM", CLAIMS),
            Arrays.asList("SPEC", DESCRIPTION),
            Arrays.asList("ANRE_CUR",LATEST_ASSIGNEES+"."+LATEST_ASSIGNEE),
            Arrays.asList("SFAM", FAMILY_ID)

    ).collect(Collectors.toMap(e->e.get(0), e->e.get(1)));

    private static Set<String> scoringAttrs = Collections.synchronizedSet(new HashSet<>());
    static {
        scoringAttrs.add("TAC");
        scoringAttrs.add("CLM");
        scoringAttrs.add("DCLM");
        scoringAttrs.add("ICLM");
        scoringAttrs.add("ACLM");
        scoringAttrs.add("TTL");
        scoringAttrs.add("ABST");
        scoringAttrs.add("CPC");
        scoringAttrs.add("SPEC");
        scoringAttrs.add(DESCRIPTION);
        scoringAttrs.add(ABSTRACT);
        scoringAttrs.add(INVENTION_TITLE);
        scoringAttrs.add(CODE);
        scoringAttrs.add(CLAIMS);
        scoringAttrs.add(SIMILARITY);
        scoringAttrs.add(ENC);
    }
    public static boolean contributesToScore(String str) {
        System.out.println("Contributes to score "+str+": "+scoringAttrs.contains(str));
        return scoringAttrs.contains(str);
    }

    public static final List<Pair<String,String>> acclaimAttrs = Collections.synchronizedList(new ArrayList<>());
    static {
        Map<String, String> primaryAcclaimMap = Attributes.ACCLAIM_IP_TO_ATTR_NAME_MAP;
        primaryAcclaimMap.forEach((k, v) -> acclaimAttrs.add(new Pair<>(k, v)));
        Set<String> values = new HashSet<>(primaryAcclaimMap.values());
        GlobalParser.transformationsForAttr.forEach((k, v) -> {
            if (!values.contains(k) && !primaryAcclaimMap.containsKey(k)) {
                acclaimAttrs.add(new Pair<>(k, SimilarPatentServer.humanAttributeFor(k)));
            }
        });
    }

    public static final Collection<String> NESTED_ATTRIBUTES = Arrays.asList(
            ASSIGNEES,
            INVENTORS,
            CITATIONS,
            RCITATIONS,
            PRIORITY_CLAIMS,
            ASSIGNMENTS,
            STANDARDS,
            PTAB
    );


    public static final Collection<String> ATTRIBUTES_WITH_SYNONYMS = Collections.synchronizedSet(new HashSet<>(Arrays.asList(
            INVENTION_TITLE,
            ABSTRACT,
            DESCRIPTION,
            CLAIMS
    )));

}
