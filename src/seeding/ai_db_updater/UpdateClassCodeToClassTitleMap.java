package seeding.ai_db_updater;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;

/**
 * Created by ehallmark on 1/25/17.
 */
public class UpdateClassCodeToClassTitleMap {
    private static final File cpcInputDataFile = new File("CPCSchemeXML201802/");
    // the file is located here: http://www.cooperativepatentclassification.org/Archive.html

    public static void main(String[] args) {
        Map<String,String> classCodeToTitleMap = Collections.synchronizedMap(new HashMap<>());

        // parse html data
        Arrays.stream(cpcInputDataFile.listFiles((dir,name)->name.endsWith(".xml"))).parallel().forEach(file->{
            try {
                parse(file, classCodeToTitleMap);
            } catch(Exception e) {
                System.out.println("Error parsing file: "+file.getName());
                System.out.println("Exception: "+e.getMessage());
            }
        });

        // save object
        Database.saveObject(classCodeToTitleMap,Database.classCodeToClassTitleMapFile);

        System.out.println("Total size: "+classCodeToTitleMap.size());
    }

    private static void parse(File file, Map<String,String> map) throws Exception {
        DocumentBuilderFactory factory
                = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        DocumentBuilder dBuilder = factory.newDocumentBuilder();
        Document doc = dBuilder.parse(file);
        NodeList classifications = doc.getElementsByTagName("classification-item");
        for (int temp = 0; temp < classifications.getLength(); temp++) {
            Node classification = classifications.item(temp);
            try {
                if (classification.getNodeType() == Node.ELEMENT_NODE) {
                    Element classElement = (Element) classification;
                    Node symbol = classElement.getFirstChild();
                    if (symbol != null) {
                        String classSymbol = symbol.getTextContent();
                        if (classSymbol.length() >= 1) {
                            Node node = symbol.getNextSibling().getFirstChild();
                            List<String> titleParts = new ArrayList<>();
                            while (node != null) {
                                if (node.getNodeType() == Node.ELEMENT_NODE) {
                                    Element elem = (Element) node;
                                    if (elem.getTagName().equals("title-part")) {
                                        NodeList references = elem.getElementsByTagName("reference");
                                        //for (int i = 0; i < references.getLength(); i++) {
                                        //    elem.remo(references.item(i));
                                        //}
                                        titleParts.add(elem.getTextContent());
                                        break;
                                    }
                                }
                                node = node.getNextSibling();
                            }
                            classSymbol=ClassCodeHandler.convertToLabelFormat(classSymbol);
                            String title = String.join("; ", titleParts);
                            //System.out.println(classSymbol + "," + title);
                            map.put(classSymbol, title);
                        }
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}
