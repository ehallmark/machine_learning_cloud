package user_interface.ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import tools.ClassCodeHandler;
import user_interface.server.SimilarPatentServer;

import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.extractString;
import static user_interface.server.SimilarPatentServer.preProcess;

/**
 * Created by ehallmark on 5/10/17.
 */
public class IncludeCPCFilter extends AbstractIncludeFilter {

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 CPC Code per line (eg. A20F 4332/203556)").withName(Constants.CPC_CODE_FILTER)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        labels = preProcess(extractString(req, Constants.CPC_CODE_FILTER, "").toUpperCase(), "\n", "[^a-zA-Z0-9 \\/]");
        labels = labels.stream().flatMap(label-> Database.subClassificationsForClass(ClassCodeHandler.convertToLabelFormat(label)).stream().map(cpc->ClassCodeHandler.convertToHumanFormat(cpc))).collect(Collectors.toList());
    }


    @Override
    public String getName() {
        return Constants.CPC_CODE_FILTER;
    }

    @Override
    public String getPrerequisite() {
        return Constants.CPC_CODES;
    }
}
