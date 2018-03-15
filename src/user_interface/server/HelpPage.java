package user_interface.server;

import org.apache.commons.io.FileUtils;

import java.io.File;

import static spark.Spark.*;
public class HelpPage {
    private static final File templateFile = new File("help/help_template.html");
    public static void helpPage() {
        final boolean debugging = true;
        if(debugging) {
            // HOST ASSETS
            staticFiles.externalLocation(new File("public").getAbsolutePath());
            port(6969);
        }
        try {
            String helpTemplate = FileUtils.readFileToString(templateFile);
            get("/help", (req,res)->{
                if(debugging) {
                    return FileUtils.readFileToString(templateFile);
                }
                return helpTemplate;
            });
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        helpPage();
    }
}
