package services;

import models.CodeValue;
import models.Exclusion;
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
    private static Map<String, Exclusion> Exclusions;

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
        Exclusions = new HashMap<>();
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
            String included = "";
            Exclusion excluded = null;
            StringBuilder extraPhrases = new StringBuilder();

            for (int i = 0; i < c.getLength(); i++) {
                Node m = c.item(i);
                String name = m.getNodeName();
                if (name.equals("diag")) break; // all done, hit the next one
                if (name.equals("name")) {
                    code = m.getFirstChild().getNodeValue();
                    excluded = FindExludes(m, code);
                    if (excluded != null) {
                        if (excluded.excludes1 != "") {
                            extraPhrases.append("Excludes: " + excluded.excludes1 + ".");
                        }
                        if (excluded.excludes2 != "") {
                            extraPhrases.append("Consider Instead: " + excluded.excludes2 + ".");
                        }
                    }
                    continue;
                }
                else if (name.equals("desc")) {
                    desc = m.getFirstChild().getNodeValue();
                }
                else if (name.equals("inclusionTerm")) {
                    NodeList notes = m.getChildNodes();
                    for(int i2=0; i2 < notes.getLength(); i2++) {

                        String note = notes.item(i2).getTextContent();
                        if (note != null && !note.equals("") && !note.equals("\n"))
                            included += note;
                    }
                }
                else if (name.equals("codeFirst")) {
                    extraPhrases.append(" Code First:" + m.getTextContent() + ".");
                }
                else if (name.equals("useAdditionalCode")) {
                    extraPhrases.append(" Use Additional Code:" + m.getTextContent() + ".");
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
                        if (included.equals("") == false) {
                            cv.desc += ". Includes: " + included;
                        }
                        if (extraPhrases.length() > 0) {
                            cv.desc += " " + extraPhrases.toString();
                        }
                        cv.url = googleUrl;
                        codeValueList.add(cv);
                    }
                }
                else {
                    CodeValue cv = new CodeValue();
                    cv.icd10Code = code;
                    cv.desc = desc;
                    cv.url = googleUrl;
                    if (included.equals("") == false) {
                        cv.desc += ". Includes: " + included;
                    }
                    if (extraPhrases.length() > 0) {
                        cv.desc += ". " + extraPhrases.toString();
                    }
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

    public Exclusion FindExludes(Node codeNode, String code) throws Exception {
        Exclusion exc = null;

        if (Exclusions.containsKey(code))
        {
            Logger.debug("Already have " + code);
            return Exclusions.get(code);
        }

        Node current = codeNode.getParentNode();
        int codeLen = code.length();

        for(int c = codeLen; c >= 3; c--) {
            if (c == 4) continue;
            String currentCode = code.substring(0,c);
            Logger.debug("code lookup " + currentCode );

            NodeList nodes = current.getChildNodes();

            String excludes1 = "";
            String excludes2 = "";

            for(int n = 0; n < nodes.getLength(); n++) {
                Node m = nodes.item(n);
                String s = m.getNodeName();
                if (s.equals("excludes1")) {
                    String notes = m.getTextContent();
                    excludes1 = notes;
                }
                else if (s.equals("excludes2")) {
                    String notes = m.getTextContent();
                    excludes2 = notes;
                }
            }

            exc = null;

            if (!excludes1.equals("") || !excludes2.equals("")) {
                exc = new Exclusion();
                exc.excludes1 = excludes1;
                exc.excludes2 = excludes2;
                Exclusions.put(currentCode, exc);
                return exc;
            }
            else {
                Exclusions.put(currentCode, null);
            }
            current = current.getParentNode();
        }

        return null;
    }
}
