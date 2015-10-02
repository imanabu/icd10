package services;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.sun.org.apache.xpath.internal.NodeSet;
import models.IcdResultSet;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import play.Logger;
import play.Play;

import java.io.File;
import java.net.URL;
import java.util.*;

import models.CodeValue;

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

    public IcdResultSet findDescription(String description) throws Exception {

        IcdResultSet resultSet = new IcdResultSet();
        List<CodeValue> codeValueList = resultSet.codeValues;

        if (description == null || description.equals(""))
        {
            return resultSet; // empty list
        }

        String[] keywords = description.split(" ");

        StringBuilder sb = new StringBuilder();
        Boolean toAnd = false;

        for(String keyword: keywords) {
            if (toAnd) sb.append(" and ");
            // sb.append(String.format("contains(.,'%s')", keyword));
            sb.append(String.format("contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),'%s')", keyword));
            toAnd = true;
        }

        XPath xpath = XPathFactory.newInstance().newXPath();
        // String expression = "//desc[contains(., description)]";
        String expression = String.format("//desc[%s]", sb.toString());
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

            String exclude = "of in at other with without and or " + description.toLowerCase();

            if (code.equals("") == false) {
                CodeValue cv = new CodeValue();
                cv.icd10Code = code;
                cv.desc = desc;
                String[] words = desc.split("[  \\t\\r\\n\\v\\f,;]");

                for(String w : words) {
                    w = w.toLowerCase();
                    if (resultSet.tags.contains(w)) continue;
                    if (exclude.indexOf(w) >= 0) continue;
                    resultSet.tags.add(w);
                }

                codeValueList.add(cv);
            }
        }

        return resultSet;
    }
}
