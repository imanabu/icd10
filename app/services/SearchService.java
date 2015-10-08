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

    private Map<String, Exclusion> Exclusions;
    private Map<String, String> Includes;
    private Map<String, String> CodeAlso;
    private Map<String, String> CodeFirst;
    private Map<String, String> UseAdditionalCode;
    private Map<String, String> InclusionTerm;

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

        Exclusions = new HashMap<>();
        Includes = new HashMap<>();
        CodeFirst = new HashMap<>();
        CodeAlso = new HashMap<>();
        UseAdditionalCode = new HashMap<>();
        InclusionTerm = new HashMap<>();

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
                switch (name) {
                    case "name":
                        code = m.getFirstChild().getNodeValue();
                        BuildExtrasMap(m, code);
                        break;
                    case "desc":
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

                String googleUrl = "https://google.com/search?q=" + URLEncoder.encode(desc, "UTF-8");
                String[] words = desc.split("[ \\t\\r\\n\\v\\f,;]");

                for(String w : words) {
                    w = w.toLowerCase().replace("(", "").replace(")","")
                    .replace("[","").replace("]","");
                    if (resultSet.tags.contains(w)) continue;
                    if (exclude.contains(w)) continue;
                    resultSet.tags.add(w);
                }

                StringBuilder sb1 = new StringBuilder();
                sb1.append(LocateExclude(code, Exclusions));
                sb1.append(" ");
                sb1.append(LocateExtra(code, Includes, " Includes: "));
                sb1.append(LocateExtra(code, InclusionTerm, " Includes term(s) of "));
                sb1.append(LocateExtra(code, CodeFirst, " First code "));
                sb1.append(LocateExtra(code, CodeAlso, " Also, code "));
                sb1.append(LocateExtra(code, UseAdditionalCode, " Additionally  "));
                String desc2 = sb1.toString();


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
                        cv.desc = desc + ", " + seventh.get(subCode) +
                                ". " + desc2;
                        cv.url = googleUrl;
                        codeValueList.add(cv);
                    }
                }
                else {
                    CodeValue cv = new CodeValue();
                    cv.icd10Code = code;
                    cv.desc = desc + ". " + desc2;
                    codeValueList.add(cv);
                }
            }
        }

        resultSet.codeValues = codeValueList;

        if (nodeCount < 2) resultSet.tags.clear();

        return resultSet;
    }

    /**
     * Loads the 7th Charcter Rules to be used for lookups.
     * @return
     * @throws Exception
     */
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

    public String LocateExtra(String code, Map<String, String> map, String label) {
        int codeLen = code.length();
        String bigNote = "";
        for(int c = codeLen; c >= 3; c--) {
            if (c == 4) continue;
            String currentCode = code.substring(0,c);
            if (map.containsKey(currentCode)) {
                String note = map.get(currentCode);
                if (note == null) continue;
                bigNote = note + " " + bigNote;
            }
        }

        if (bigNote.equals("")) return "";
        return label + " " + bigNote;
    }

    public String LocateExclude(String code, Map<String, Exclusion> map) {

        int codeLen = code.length();
        for(int c = codeLen; c >= 3; c--) {
            if (c == 4) continue;
            String currentCode = code.substring(0,c);
            if (map.containsKey(currentCode))
            {
                StringBuilder sb = new StringBuilder();
                Exclusion e = map.get(currentCode);

                if (e == null) continue;

                if (e.excludes1 != null && e.excludes1.equals("") == false)
                {
                    sb.append("Excludes ");
                    sb.append(e.excludes1);
                }

                if (e.excludes2 != null && e.excludes2.equals("") == false)
                {
                    if (sb.length() != 0) sb.append(". ");
                    sb.append("Consider ");
                    sb.append(e.excludes2);
                }

                sb.append(". ");

                return sb.toString();
            }
        }
        return "";
    }

    /**
     * Builds the extra data maps like Excludes, Includes, CodeFirst and CodeTogether items
     * in the tree.
     * @param codeNode
     * @param code
     * @return
     * @throws Exception
     */
    public void BuildExtrasMap(Node codeNode, String code) throws Exception {

        if (Exclusions.containsKey(code) == false) {

            Exclusion exc = null;
            Node current = codeNode.getParentNode();
            int codeLen = code.length();

            for(int c = codeLen; c >= 3; c--) {
                if (c == 4) continue;
                String currentCode = code.substring(0,c);
                Logger.debug("code lookup " + currentCode );

                NodeList nodes = current.getChildNodes();

                String excludes1 = null;
                String excludes2 = null;

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

                if (excludes1 != null || excludes2 != null) {
                    if (exc == null) exc = new Exclusion();
                    if (excludes1 != null)
                        exc.excludes1 = exc.excludes1 == null?
                            excludes1 : exc.excludes1 + " " + excludes1;
                    if (excludes2 != null)
                        exc.excludes2 = exc.excludes2 == null?
                            excludes2 : exc.excludes2 + " " + excludes2;
                    Exclusions.put(currentCode, exc);

                }
                else {
                    Exclusions.put(currentCode, null);
                }
                current = current.getParentNode();
            }
        }

        if (Includes.containsKey(code) == false) {

            Node current = codeNode.getParentNode();
            int codeLen = code.length();

            for(int c = codeLen; c >= 3; c--) {
                if (c == 4) continue;
                String currentCode = code.substring(0,c);
                NodeList nodes = current.getChildNodes();
                String notes = "";

                for(int n = 0; n < nodes.getLength(); n++) {
                    Node m = nodes.item(n);
                    String s = m.getNodeName();
                    if (s.equals("includes")) {
                        notes = m.getTextContent();
                    }
                }

                if (notes.equals("") == false) {
                    Includes.put(currentCode, notes);
                }
                else {
                    Includes.put(currentCode, null);
                }
                current = current.getParentNode();
            }
        }

        if (CodeFirst.containsKey(code) == false) {

            Node current = codeNode.getParentNode();
            int codeLen = code.length();

            for(int c = codeLen; c >= 3; c--) {
                if (c == 4) continue;
                String currentCode = code.substring(0,c);
                NodeList nodes = current.getChildNodes();
                String notes = "";

                for(int n = 0; n < nodes.getLength(); n++) {
                    Node m = nodes.item(n);
                    String s = m.getNodeName();
                    if (s.equals("codeFirst")) {
                        notes = m.getTextContent();
                    }
                }

                if (notes.equals("") == false) {
                    CodeFirst.put(currentCode, notes);
                }
                else {
                    CodeFirst.put(currentCode, null);
                }
                current = current.getParentNode();
            }
        }

        if (UseAdditionalCode.containsKey(code) == false) {

            Node current = codeNode.getParentNode();
            int codeLen = code.length();

            for(int c = codeLen; c >= 3; c--) {
                if (c == 4) continue;
                String currentCode = code.substring(0,c);
                NodeList nodes = current.getChildNodes();
                String notes = "";

                for(int n = 0; n < nodes.getLength(); n++) {
                    Node m = nodes.item(n);
                    String s = m.getNodeName();
                    if (s.equals("useAdditionalCode")) {
                        notes = m.getTextContent();
                    }
                }

                if (notes.equals("") == false) {
                    UseAdditionalCode.put(currentCode, notes);
                }
                else {
                    UseAdditionalCode.put(currentCode, null);
                }
                current = current.getParentNode();
            }
        }

        if (CodeAlso.containsKey(code) == false) {
            Node current = codeNode.getParentNode();
            int codeLen = code.length();

            for(int c = codeLen; c >= 3; c--) {
                if (c == 4) continue;
                String currentCode = code.substring(0,c);
                NodeList nodes = current.getChildNodes();
                String notes = "";

                for(int n = 0; n < nodes.getLength(); n++) {
                    Node m = nodes.item(n);
                    String s = m.getNodeName();
                    if (s.equals("codeAlso")) {
                        notes = m.getTextContent();
                    }
                }

                if (notes.equals("") == false) {
                    CodeAlso.put(currentCode, notes);
                }
                else {
                    CodeAlso.put(currentCode, null);
                }
                current = current.getParentNode();
            }
        }

        if (InclusionTerm.containsKey(code) == false) {
            Node current = codeNode.getParentNode();
            int codeLen = code.length();

            for(int c = codeLen; c >= 3; c--) {
                if (c == 4) continue;
                String currentCode = code.substring(0,c);
                NodeList nodes = current.getChildNodes();
                String notes = "";

                for(int n = 0; n < nodes.getLength(); n++) {
                    Node m = nodes.item(n);
                    String s = m.getNodeName();
                    if (s.equals("inclusionTerm")) {
                        notes = m.getTextContent();
                    }
                }

                if (notes.equals("") == false) {
                    InclusionTerm.put(currentCode, notes);
                }
                else {
                    InclusionTerm.put(currentCode, null);
                }
                current = current.getParentNode();
            }
        }
        return;
    }
}
