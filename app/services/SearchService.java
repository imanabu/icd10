package services;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.sun.org.apache.xpath.internal.NodeSet;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import play.Logger;
import play.Play;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by manabutokunaga on 10/1/15.
 */
public class SearchService {

    private static Document Icd10Doc = null;

    public SearchService() throws Exception
    {
        if (Icd10Doc != null) return;
        URL icd10Uri = Play.application().classloader().getResource("icd10/Tabular.xml");
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        String path = icd10Uri.toString().replace("file:", "");
        Logger.debug(path);
        Icd10Doc = builder.parse(new File(path));
    }

    public Map<String, String> findDescription(String description) throws Exception {

        Map<String, String> codeValue = new HashMap<>();
        XPath xpath = XPathFactory.newInstance().newXPath();
        // String expression = "ICD10CM.tabular//version";
        String expression = "//desc[contains(., description)]";
        expression = String.format("//desc[contains(.,%s)]", description);
        // Node node = (Node) xpath.evaluate(expression, Icd10Doc, XPathConstants.NODE);
        NodeList nodeList = (NodeList) xpath.evaluate(expression, Icd10Doc, XPathConstants.NODESET);

        Logger.debug("nodes found {}", nodeList.getLength());

        for(int j = 0; j < nodeList.getLength(); j++){
            Node node = nodeList.item(j).getParentNode();
            NodeList c = node.getChildNodes();

            String code = "";
            String desc = "";

            for (int i = 0; i < c.getLength(); i++) {
                Node m = c.item(i);
                if (m.getNodeName().equals("name")) {
                    code = m.getFirstChild().getNodeValue();
                    continue;
                }

                if (m.getNodeName().equals("desc")) {
                    desc = m.getFirstChild().getNodeValue();
                    break;
                }
            }

            if (code.equals("") == false) {
                codeValue.put(code, desc);
            }
        }

        return codeValue;
    }
}
