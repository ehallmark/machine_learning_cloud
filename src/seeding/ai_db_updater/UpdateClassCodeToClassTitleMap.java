package seeding.ai_db_updater;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import seeding.Constants;
import seeding.Database;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;

/**
 * Created by ehallmark on 1/25/17.
 */
public class UpdateClassCodeToClassTitleMap {
    public static final File mapFile = new File(Constants.DATA_FOLDER+"class_code_to_class_title_map.jobj");
    private static final File cpcInputDataFile = new File(Constants.DATA_FOLDER+"CPCSchemeXML201705/");
    // the file is located here: http://www.cooperativepatentclassification.org/Archive.html

    public static void main(String[] args) {
        Map<String,String> classCodeToTitleMap = Collections.synchronizedMap(new HashMap<>());

        // parse html data
        System.out.println("File exists? "+cpcInputDataFile.exists());
        Arrays.stream(cpcInputDataFile.listFiles((dir,name)->name.endsWith(".xml")&&!name.chars().anyMatch(c->Character.isDigit(c)))).parallel().forEach(file->{
            try {
                System.out.println("Parsing file: "+file.getName());
                parse(file, classCodeToTitleMap);
            } catch(Exception e) {
                System.out.println("Error parsing file: "+file.getName());
                System.out.println("Exception: "+e.getMessage());
            }
        });

        // save object
        Database.saveObject(classCodeToTitleMap,mapFile);

        System.out.println("Total size: "+classCodeToTitleMap.size());
    }

    private static void parse(File file, Map<String,String> map) throws Exception {
        DocumentBuilderFactory factory
                = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        DocumentBuilder dBuilder = factory.newDocumentBuilder();
        Document doc = dBuilder.parse(file);
        System.out.println("Root element :"
                + doc.getDocumentElement().getNodeName());
        NodeList classifications = doc.getElementsByTagName("classification-item");
        System.out.println("----------------------------");
        for (int temp = 0; temp < classifications.getLength(); temp++) {
            Node classification = classifications.item(temp);
            if (classification.getNodeType() == Node.ELEMENT_NODE) {
                Element classElement = (Element) classification;
                Node symbol = classElement.getFirstChild();
                if(symbol!=null) {
                    String classSymbol = symbol.getTextContent();
                    if(classSymbol.length()==3) {
                        Node node = symbol.getNextSibling().getFirstChild();
                        List<String> titleParts = new ArrayList<>();
                        while (node != null) {
                            if (node.getNodeType() == Node.ELEMENT_NODE) {
                                Element elem = (Element) node;
                                if (elem.getTagName().equals("title-part")) {
                                    titleParts.add(elem.getTextContent());
                                    break;
                                }
                            }
                            node = node.getNextSibling();
                        }
                        String title = String.join("; ",titleParts);
                        System.out.println(classSymbol+": " + title);
                        map.put(classSymbol, title);
                    }
                }
            }
        }
    }

    public static String getFullClassTitleFromClassCode(String formattedCode, Map<String,String> classCodeToClassTitleMap) {
        formattedCode=formattedCode.toUpperCase().replaceAll(" ","");
        if(classCodeToClassTitleMap.containsKey(formattedCode)) return classCodeToClassTitleMap.get(formattedCode);
        return "";
    }

}
