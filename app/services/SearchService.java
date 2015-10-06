package services;

import models.CodeValue;
import models.IcdResultSet;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import play.Logger;
import play.Play;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ICD-10 Search Service
 * Created by manabutokunaga on 10/1/15.
 */
public class SearchService {

    private static Document Icd10Doc = null;
    private static Map<String, Map<String, String>> SeventhCharRules;

    public SearchService() throws Exception
    {
        if (Icd10Doc != null) return;
        URL icd10Uri = Play.application().classloader().getResource("icd10/Tabular.xml");
        if (icd10Uri == null) {
            Logger.error("icd10/Tabular.xml should be found in config directory");
            return;
        }
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        String path = icd10Uri.toString().replace("file:", "");
        Logger.debug(path);
        Icd10Doc = builder.parse(new File(path));
        SeventhCharRules = Load7CharRules();
    }

    public IcdResultSet findDescription(String description) throws Exception {

        IcdResultSet resultSet = new IcdResultSet();
        List<CodeValue> codeValueList = new ArrayList<>();

        if (description == null || description.equals(""))
        {
            return resultSet; // empty list
        }

        resultSet.tags.clear();
        String[] keywords = description.toLowerCase()
                .replace("*", "")
                .split(" ");

        StringBuilder sb = new StringBuilder();
        Boolean toAnd = false;

        for(String keyword: keywords) {
            if (toAnd) sb.append(" and ");
            String encKeyword =  keyword.replace("'s", "");
            // Hack to make toLower() like search to work in V1.0 of XPATH Java Lib
            sb.append(String.format(
                    "contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),'%s')",
                    encKeyword));
            toAnd = true;
        }

        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = String.format("//desc[%s]", sb.toString());
        NodeList nodeList = (NodeList) xpath.evaluate(expression, Icd10Doc, XPathConstants.NODESET);

        Logger.debug("nodes found {}", nodeList.getLength());
        int nodeCount = nodeList.getLength();

        Map<String, String> seventh;

        for(int j = 0; j < nodeCount; j++){
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

            String exclude = "of in at other is are and or to from not on by" + description.toLowerCase();

            if (!code.equals("")) {

                seventh = null;

                for (int cl = code.length() - 1; cl >= 2; cl--)
                {
                    String csub = code.substring(0, cl);

                    if (SeventhCharRules.containsKey(csub))
                    {
                        seventh = SeventhCharRules.get(csub);
                        break;
                    }
                }

                // if (code.contains(seventhBase) == false) seventh = null;

                String googleUrl = "https://google.com/search?q=" + URLEncoder.encode(desc, "UTF-8");

                String[] words = desc.split("[ \\t\\r\\n\\v\\f,;]");

                for(String w : words) {
                    w = w.toLowerCase().replace("(", "").replace(")","")
                    .replace("[","").replace("]","");
                    if (resultSet.tags.contains(w)) continue;
                    if (exclude.contains(w)) continue;
                    resultSet.tags.add(w);
                }

                if (seventh != null && code.length() > 3)
                {
                    // Makes shorter codes to comply with full 7-place. If sub-code is
                    // not known, an X will be placed on lower digits.
                    for(String subCode: seventh.keySet()) {
                        CodeValue cv = new CodeValue();
                        String code2 = code;
                        while(code2.length() < 7)
                        {
                            code2 = code2 + "X";
                        }
                        cv.icd10Code = code2 + subCode;
                        resultSet.subCodes.add(subCode);
                        cv.desc = desc + ", " + seventh.get(subCode);
                        cv.url = googleUrl;
                        codeValueList.add(cv);
                    }
                }
                else {
                    CodeValue cv = new CodeValue();
                    cv.icd10Code = code;
                    cv.desc = desc;
                    cv.url = googleUrl;
                    codeValueList.add(cv);
                }
            }
        }

        resultSet.codeValues = codeValueList;

        if (nodeCount < 2) resultSet.tags.clear();

        return resultSet;
    }

    public Map<String, Map<String, String>> Load7CharRules() throws Exception {

        Map<String, Map<String, String>> sevenCharRuleSet = new HashMap<>();
        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = "//sevenChrDef/..";
        NodeList nodeList = (NodeList) xpath.evaluate(expression, Icd10Doc, XPathConstants.NODESET);
        int ruleNodesCount = nodeList.getLength();

        for(int n = 0; n < ruleNodesCount; n++) {

            Node ruleParent = nodeList.item(n);
            NodeList nodes = ruleParent.getChildNodes();
            int nodesCount = nodes.getLength();
            String code = "";
            Map<String, String> seventh = new HashMap<>();

            for(int i = 0; i < nodesCount; i++)
            {
                Node node = nodes.item(i);
                String nodeName = node.getNodeName();

                if (nodeName.equals("name"))
                {
                    code = node.getFirstChild().getNodeValue();
                    // Logger.debug(code);
                }
                else if (nodeName.equals("sevenChrDef")) {
                    NodeList ruleLetters = node.getChildNodes();
                    for(int k = 0; k < ruleLetters.getLength(); k++)
                    {
                        Node item = ruleLetters.item(k);
                        if (item.getNodeName().equals("extension")) {
                            String key = item.getAttributes().getNamedItem("char").getNodeValue();
                            String desc = item.getFirstChild().getNodeValue();
                            seventh.put(key, desc);
                        }
                    }
                    // Found both name and the set -- in the future this break may need to be relocated
                    break;
                }
            }
            sevenCharRuleSet.put(code, seventh);
        }
        return sevenCharRuleSet;
    }
}
