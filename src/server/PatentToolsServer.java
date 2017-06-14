package server;

import com.google.gson.Gson;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import seeding.GetEtsiPatentsList;
import seeding.patent_view_api.PatentAPIHandler;
import server.tools.SimpleAjaxMessage;
import tools.ClassCodeHandler;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static j2html.TagCreator.td;
import static j2html.TagCreator.tr;
import static spark.Spark.get;
import static spark.Spark.post;

/**
 * Created by Evan on 5/17/2017.
 */
public class PatentToolsServer {
    private static final String ASSIGNEE_ASSET_COUNT_FORM_ID = "select-assignee-asset-count-form";
    private static final String ASSIGNEE_ASSETS_FORM_ID = "select-assignee-assets-form";
    private static final String PATENTS_FROM_ETSI_FORM_ID = "select-patents-from-etsi-form";
    private static final String CPC_FROM_ASSETS_FORM_ID = "select-cpc-from-assets-form";
    private static final String TITLE_FROM_ASSETS_FORM_ID = "select-title-from-assets-form";
    private static final String CPC_TO_ASSETS_FORM_ID = "select-cpc-to-assets-form";
    private static final String CPC_FREQUENCY_FROM_ASSETS_FORM_ID = "select-cpc-frequency-from-assets-form";
    private static final String TECH_PREDICTION_FROM_ASSETS_FORM_ID = "tech-from-assets-form";
    private static final String TECH_PREDICTION_FROM_ASSIGNEES_FORM_ID = "tech-from-assignees-form";
    private static final String TECH_PREDICTION_FROM_CPCS_FORM_ID = "tech-from-cpcs-form";

    public static void setup() {
        get("/patent_toolbox", (req, res) -> SimilarPatentServer.templateWrapper(res, div().with(patentToolboxForm(), hr()), SimilarPatentServer.getAndRemoveMessage(req.session())));

        // POST METHODS
        post("/assignee_asset_count", (req, res) -> {
            res.type("application/json");
            String assigneeStr = req.queryParams("assignee");
            if(assigneeStr==null||assigneeStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one assignee"));

            String[] assignees = assigneeStr.split(System.getProperty("line.separator"));
            if(assignees==null||assignees.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one assignee"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Assignee"),
                                    th("Approx. Asset Count")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(assignees)
                                    .filter(assignee->!(assignee==null||assignee.isEmpty()))
                                    .map(assignee->tr().with(
                                            td(assignee),
                                            td(String.valueOf(Database.getAssetCountFor(assignee))))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

        post("/assignee_assets", (req, res) -> {
            res.type("application/json");
            String assigneeStr = req.queryParams("assignee");
            if(assigneeStr==null||assigneeStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one assignee"));

            String[] assignees = assigneeStr.split(System.getProperty("line.separator"));
            if(assignees==null||assignees.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one assignee"));
            boolean usePatentsView = SimilarPatentServer.extractBool(req,"use_patents_view");
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Assignee"),
                                    th("Assets")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(assignees)
                                    .filter(assignee->!(assignee==null||assignee.isEmpty()))
                                    .map(assignee->tr().with(
                                            td(assignee),
                                            td(String.join(" ",usePatentsView ? PatentAPIHandler.requestPatentNumbersFromAssignee(assignee)
                                                    : Database.selectPatentNumbersFromAssignee(assignee))))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

        post("/tech_predictions", (req, res) -> {
            res.type("application/json");
            String type = req.queryParams("report_type");
            if(type==null||!Arrays.asList("patents","assignees","class_codes").contains(type)) {
                return new Gson().toJson(new SimpleAjaxMessage("Unknown report type"));
            }
            String asset = req.queryParams("item");
            if(asset==null||asset.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one input"));
            PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(type);
            String[] assets = asset.split(System.getProperty("line.separator"));
            if(assets==null||assets.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one input"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th(type.substring(0,1).toUpperCase()+type.substring(1).replaceAll("_"," ")),
                                    th("Probability"),
                                    th("Technology")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(assets)
                                    .filter(assignee->!(assignee==null||assignee.isEmpty()))
                                    .map(item->{
                                        System.out.println("Item length: "+item.length());
                                        item = item.trim();
                                        System.out.println("Item length after trim: "+item.length());
                                        final String prettyItem = item;
                                        List<Pair<String,Double>> pairs = SimilarPatentServer.getTagger().attributesFor(Arrays.asList(item),1);
                                        String val = "";
                                        double probability = 0.0;
                                        if(!pairs.isEmpty()) {
                                            Pair<String,Double> pair = pairs.get(0);
                                            val+=pair.getFirst();
                                            probability+=pair.getSecond();
                                        }
                                        return tr().with(
                                                td(prettyItem),
                                                td(String.valueOf(probability)),
                                                td(val)
                                        );
                                    }).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

        post("/etsi_standards", (req, res) -> {
            res.type("application/json");
            String etsiStr = req.queryParams("etsi_standard");
            if(etsiStr==null||etsiStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one ETSI Standard"));

            String[] standards = etsiStr.split(System.getProperty("line.separator"));
            if(standards==null||standards.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one ETSI Standard"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("ETSI Standard"),
                                    th("Assets")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(standards)
                                    .filter(standard->!(standard==null||standard.isEmpty()))
                                    .map(standard->tr().with(
                                            td(standard),
                                            td(String.join(" ",Database.selectPatentNumbersFromETSIStandard(GetEtsiPatentsList.cleanETSIString(standard)))))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });




        post("/cpc_to_assets", (req, res) -> {
            res.type("application/json");
            String classCodeStr = req.queryParams("class_code");
            if(classCodeStr==null||classCodeStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Class Code"));
            boolean includeSubclasses = SimilarPatentServer.extractBool(req, "includeSubclasses");

            String[] classCodes = classCodeStr.split(System.getProperty("line.separator"));
            if(classCodes==null||classCodes.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Class Code"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Class Code"),
                                    th("Assets")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(classCodes)
                                    .filter(code->!(code==null||code.isEmpty()))
                                    .map(code->tr().with(
                                            td(code),
                                            td(String.join(" ",(includeSubclasses?Database.selectPatentNumbersFromClassAndSubclassCodes(ClassCodeHandler.convertToLabelFormat(code)):Database.selectPatentNumbersFromExactClassCode(ClassCodeHandler.convertToLabelFormat(code))))))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

        post("/title_from_assets", (req, res) -> {
            res.type("application/json");
            String patentStr = req.queryParams("patent");
            if(patentStr==null||patentStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));

            String[] patents = patentStr.split("\\s+");
            if(patents==null||patents.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Asset"),
                                    th("Title")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(patents)
                                    .filter(patent->!(patent==null||patent.isEmpty()))
                                    .map(patent->tr().with(
                                            td(patent),
                                            td(Database.getInventionTitleFor(patent)))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });


        post("/cpc_from_assets", (req, res) -> {
            res.type("application/json");
            String patentStr = req.queryParams("patent");
            if(patentStr==null||patentStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));
            boolean includeSubclasses = SimilarPatentServer.extractBool(req, "includeSubclasses");

            String[] patents = patentStr.split(System.getProperty("line.separator"));
            if(patents==null||patents.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Asset"),
                                    th("Classifications")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(patents)
                                    .filter(patent->!(patent==null||patent.isEmpty()))
                                    .map(patent->tr().with(
                                            td(patent),
                                            td(String.join("; ",(includeSubclasses?Database.subClassificationsForPatent(patent.replaceAll("[^0-9]","")):Database.classificationsFor(patent.replaceAll("[^0-9]",""))).stream()
                                                    .map(cpc->ClassCodeHandler.convertToHumanFormat(cpc)).collect(Collectors.toList()))))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

        post("/cpc_frequencies_from_assets", (req, res) -> {
            res.type("application/json");
            String patentStr = req.queryParams("patent");
            if(patentStr==null||patentStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));
            boolean includeSubclasses = SimilarPatentServer.extractBool(req, "includeSubclasses");

            String[] patents = patentStr.split("\\s+");
            if(patents==null||patents.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));

            AtomicInteger cnt = new AtomicInteger(0);
            Map<String,Double> classScoreMap = new HashMap<>();
            Arrays.stream(patents).forEach(patent->{
                if(patent==null||patent.trim().isEmpty()) return;
                Collection<String> data = (includeSubclasses?Database.subClassificationsForPatent(patent.replaceAll("[^0-9]","")):Database.classificationsFor(patent.replaceAll("[^0-9]","")));
                if(data.isEmpty()) return;
                data.stream()
                        .forEach(cpc->{
                            if(classScoreMap.containsKey(cpc)) {
                                classScoreMap.put(cpc,classScoreMap.get(cpc)+1.0);
                            } else {
                                classScoreMap.put(cpc,1.0);
                            }
                        });
                cnt.getAndIncrement();
            });

            Map<String,Double> globalFrequencyMap = new HashMap<>();
            Map<String,Double> ratioMap = new HashMap<>();
            // standardize numbers
            Set<String> keys = new HashSet<>(classScoreMap.keySet());
            keys.forEach(key->{
                classScoreMap.put(key,classScoreMap.get(key)/cnt.get());
                globalFrequencyMap.put(key,new Double(Database.selectPatentNumbersFromExactClassCode(key).size())/Database.numPatentsWithCpcClassifications());
                ratioMap.put(key,classScoreMap.get(key)*Math.log(1.0+globalFrequencyMap.get(key)));
            });

            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Class Code"),
                                    th("Frequency in Portfolio"),
                                    th("Global Frequency"),
                                    th("Frequency Score (f_p*log(1+f_g))")
                            )
                    ),
                    tbody().with(
                            ratioMap.entrySet().stream()
                                    .sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                                    .map(e->tr().with(
                                            td(ClassCodeHandler.convertToHumanFormat(e.getKey())),
                                            td(classScoreMap.get(e.getKey()).toString()),
                                            td(globalFrequencyMap.get(e.getKey()).toString()),
                                            td(e.getValue().toString())
                                    )).collect(Collectors.toList())
                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

    }


    private static Tag patentToolboxForm() {
        return div().with(
                SimilarPatentServer.formScript(ASSIGNEE_ASSET_COUNT_FORM_ID, "/assignee_asset_count", "Search", true),
                SimilarPatentServer.formScript(ASSIGNEE_ASSETS_FORM_ID, "/assignee_assets", "Search", true),
                SimilarPatentServer.formScript(PATENTS_FROM_ETSI_FORM_ID, "/etsi_standards", "Search", true),
                SimilarPatentServer.formScript(CPC_TO_ASSETS_FORM_ID, "/cpc_to_assets", "Search", true),
                SimilarPatentServer.formScript(CPC_FROM_ASSETS_FORM_ID, "/cpc_from_assets", "Search", true),
                SimilarPatentServer.formScript(TITLE_FROM_ASSETS_FORM_ID, "/title_from_assets", "Search", true),
                SimilarPatentServer.formScript(CPC_FREQUENCY_FROM_ASSETS_FORM_ID, "/cpc_frequencies_from_assets", "Search",true),
                SimilarPatentServer.formScript(TECH_PREDICTION_FROM_ASSETS_FORM_ID,"/tech_predictions", "Search", true),
                SimilarPatentServer.formScript(TECH_PREDICTION_FROM_ASSIGNEES_FORM_ID,"/tech_predictions", "Search", true),
                SimilarPatentServer.formScript(TECH_PREDICTION_FROM_CPCS_FORM_ID,"/tech_predictions", "Search", true),
                p("(Warning: Data only available from 2007 and onwards)"),
                table().with(
                        tbody().with(
                                tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Asset Count for Assignees (Approximation Only)"),
                                                h4("Please place each assignee on a separate line"),
                                                form().withId(ASSIGNEE_ASSET_COUNT_FORM_ID).with(
                                                        label("Assignees"),br(),textarea().withName("assignee"), br(),
                                                        button("Search").withId(ASSIGNEE_ASSET_COUNT_FORM_ID+"-button").withType("submit")
                                                )
                                        ),
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Current Assets for Assignees (Approximation Only)"),
                                                h4("Please place each assignee on a separate line"),
                                                form().withId(ASSIGNEE_ASSETS_FORM_ID).with(
                                                        label("Assignees"),br(),textarea().withName("assignee"), br(),
                                                        label("Use Patents View API?"),br(),input().withType("checkbox").withName("use_patents_view"), br(),
                                                        button("Search").withId(ASSIGNEE_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        ),
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Current Assets for ETSI Standard (Approximation Only)"),
                                                h4("Please place each ETSI Standard on a separate line"),
                                                form().withId(PATENTS_FROM_ETSI_FORM_ID).with(
                                                        label("ETSI Standards"),br(),textarea().withName("etsi_standard"), br(),
                                                        button("Search").withId(PATENTS_FROM_ETSI_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                ),tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get patents from CPC (Approximation Only)"),
                                                h4("Please place each CPC code on a separate line"),
                                                form().withId(CPC_TO_ASSETS_FORM_ID).with(
                                                        label("CPC Class Codes"),br(),textarea().withName("class_code"), br(),
                                                        label("Include CPC Subclasses?"),br(),input().withType("checkbox").withName("includeSubclasses"),br(),
                                                        button("Search").withId(CPC_TO_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        ),
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get CPC for patents (Approximation Only)"),
                                                h4("Please place each patent on a separate line"),
                                                form().withId(CPC_FROM_ASSETS_FORM_ID).with(
                                                        label("Patents"),br(),textarea().withName("patent"), br(),
                                                        label("Include CPC Subclasses?"),br(),input().withType("checkbox").withName("includeSubclasses"),br(),
                                                        button("Search").withId(CPC_FROM_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        ),td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get CPC frequencies for patents (Approximation Only)"),
                                                h4("Please place each patent on a separate line"),
                                                form().withId(CPC_FREQUENCY_FROM_ASSETS_FORM_ID).with(
                                                        label("Patents"),br(),textarea().withName("patent"), br(),
                                                        label("Include CPC Subclasses?"),br(),input().withType("checkbox").withName("includeSubclasses"),br(),
                                                        button("Search").withId(CPC_FREQUENCY_FROM_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                ),tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Technology from Patents (Approximation Only)"),
                                                h4("Please place each Patent on a separate line"),
                                                form().withId(TECH_PREDICTION_FROM_ASSETS_FORM_ID).with(
                                                        input().withType("hidden").withName("report_type").withValue("patents"),
                                                        label("Patents"),br(),textarea().withName("item"), br(),
                                                        button("Search").withId(TECH_PREDICTION_FROM_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        ),
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Technology from Assignees (Approximation Only)"),
                                                h4("Please place each Assignee on a separate line"),
                                                form().withId(TECH_PREDICTION_FROM_ASSIGNEES_FORM_ID).with(
                                                        input().withType("hidden").withName("report_type").withValue("assignees"),
                                                        label("Assignees"),br(),textarea().withName("item"), br(),
                                                        button("Search").withId(TECH_PREDICTION_FROM_ASSIGNEES_FORM_ID+"-button").withType("submit")
                                                )
                                        ),td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Technology from CPC Codes (Approximation Only)"),
                                                h4("Please place each CPC Code on a separate line"),
                                                form().withId(TECH_PREDICTION_FROM_CPCS_FORM_ID).with(
                                                        input().withType("hidden").withName("report_type").withValue("class_codes"),
                                                        label("CPC Codes"),br(),textarea().withName("item"), br(),
                                                        button("Search").withId(TECH_PREDICTION_FROM_CPCS_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                ),tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Invention Title for patents"),
                                                h4("Please place each patent on a separate line"),
                                                form().withId(TITLE_FROM_ASSETS_FORM_ID).with(
                                                        label("Patents"),br(),textarea().withName("patent"), br(),
                                                        button("Search").withId(TITLE_FROM_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        )

                                )
                        )
                ),
                br(),
                br()
        );
    }
}
