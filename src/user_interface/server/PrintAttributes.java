package user_interface.server;

import java.util.StringJoiner;

public class PrintAttributes {
    public static void main(String[] args) {
        SimilarPatentServer.loadAttributes(false,false);

        StringJoiner list = new StringJoiner("\n");
        SimilarPatentServer.getAllTopLevelAttributes().stream().sorted((a1,a2)->SimilarPatentServer.humanAttributeFor(a1.getName()).compareTo(SimilarPatentServer.humanAttributeFor(a2.getName()))).forEach(attr->{
            String div = "<div id=\"header-attributes-list-"+attr.getName().replace(".","-")+"\">";
            String h5 = "<h5>"+SimilarPatentServer.humanAttributeFor(attr.getName())+"</h5>";
            String p = "<p>\n"+attr.getDescription()+"\n</p>";
            p = p.replace("<span>","").replace("</span>","");
            String endDiv = "</div>";

            list.add(div+"\n"+h5+"\n"+p+"\n"+endDiv);
        });

        String template = list.toString();
        System.out.println("Template: \n"+template);
    }
}
