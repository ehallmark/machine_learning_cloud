package ai_db_updater;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import javax.xml.validation.ValidatorHandler;
import java.io.*;
import java.util.*;

/**
 * Created by ehallmark on 1/25/17.
 */
public class UpdateClassCodeToClassTitleMap {
    public static final File mapFile = new File("class_code_to_class_title_map.jobj");
    private static final File cpcInputDataFile = new File("cpc_xml/");
    // the file is located here: http://www.cooperativepatentclassification.org/Archive.html

    public static void main(String[] args) {
        Map<String,String> classCodeToTitleMap = new HashMap<>();

        // parse html data
        Arrays.stream(cpcInputDataFile.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml");
            }
        })).forEach(file->{
            try {
                parse(file, classCodeToTitleMap);
            } catch(Exception e) {
                System.out.println("Error parsing file: "+file.getName());
                System.out.println("Exception: "+e.getMessage());
            }
        });

        // save object
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(mapFile)));
            oos.writeObject(classCodeToTitleMap);
            oos.flush();
            oos.close();
        }catch(IOException e) {
            e.printStackTrace();
        }
        // test
        String testClass = "H04W4/00";
        String fullTitle = getFullClassTitleFromClassCode(testClass,classCodeToTitleMap);
        System.out.println("Title for "+testClass+": "+fullTitle);
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
                    Node node = symbol.getNextSibling().getFirstChild();
                    List<String> titleParts = new ArrayList<>();
                    while(node!=null) {
                        if(node.getNodeType() == Node.ELEMENT_NODE) {
                            Element elem = (Element) node;
                            if(elem.getTagName().equals("title-part")) {
                                titleParts.add(elem.getTextContent());
                            }
                        }
                        node = node.getNextSibling();
                    }
                    System.out.println("Symbol: " + classSymbol);
                    System.out.println("Title: " + String.join("; ", titleParts));
                    map.put(classSymbol, String.join("; ", titleParts));
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
