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
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * ICD-10 Search Service
 * Created by manabu@wingumd.com on 10/1/15.
 */
public class SearchService {

    private static Document Icd10Doc = null;
    private static Map<String, Map<String, String>> SeventhCharRules;
    private static Pattern icd10QueryPattern = Pattern.compile("[A-Za-z]\\d\\d\\.*");
    private static Pattern icdCodePattern = Pattern.compile("\\([A-Z]\\d\\d.*\\)");
    private Map<String, String> Excludes1;
    private Map<String, String> Excludes2;
    private Map<String, String> Includes;
    private Map<String, String> CodeAlso;
    private Map<String, String> CodeFirst;
    private Map<String, String> UseAdditionalCode;
    private Map<String, String> InclusionTerm;

    public SearchService() throws Exception {
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
        SeventhCharRules = load7thCharRules();
    }

    /**
     * find ICD-10 by description or a single code
     * @param query
     * @param detailThreshold - Optimization. Not to execute deeper query until the total nodes is less than this number.
     * @return The result set
     * @throws Exception
     */
    public IcdResultSet findDescription(String query, int detailThreshold) throws Exception {

        Excludes1 = new HashMap<>();
        Excludes2 = new HashMap<>();
        Includes = new HashMap<>();
        CodeFirst = new HashMap<>();
        CodeAlso = new HashMap<>();
        UseAdditionalCode = new HashMap<>();
        InclusionTerm = new HashMap<>();

        IcdResultSet resultSet = new IcdResultSet();
        List<CodeValue> codeValueList = new ArrayList<>();

        query = query == null ? null : query.trim();

        if (query == null || query.equals("")) {
            return resultSet; // empty list
        }

        resultSet.tags.clear();
        String[] keywords = query.trim().toLowerCase()
                .replace("*", "")
                .split(" ");

        StringBuilder sb = new StringBuilder();
        Boolean toAnd = false;

        for (String keyword : keywords) {
            if (toAnd) sb.append(" and ");
            String encKeyword = keyword.replace("'s", "");
            // Hack to make toLower() like search to work in V1.0 of XPATH Java Lib
            sb.append(String.format(
                    "contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),'%s')",
                    encKeyword));
            toAnd = true;
        }

        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression;

        Matcher matcher = icd10QueryPattern.matcher(sb.toString());

        if (matcher.find()) {
            expression = String.format("//name[text()='%s']", keywords[0].toUpperCase());
        }
        else {
            expression = String.format("//desc[%s]", sb.toString());
        }
        NodeList nodeList = (NodeList) xpath.evaluate(expression, Icd10Doc, XPathConstants.NODESET);

        Logger.debug("nodes found {}", nodeList.getLength());
        int nodeCount = nodeList.getLength();

        Map<String, String> seventh;

        for (int j = 0; j < nodeCount; j++) {
            Node node = nodeList.item(j).getParentNode();
            NodeList c = node.getChildNodes();
            List<String> bc = null;

            String code = "";
            String desc = "";
            int billability = 0;

            for (int i = 0; i < c.getLength(); i++) {
                Node m = c.item(i);
                String name = m.getNodeName();
                if (name.equals("diag")) break; // all done, hit the next one
                switch (name) {
                    case "name":
                        code = m.getFirstChild().getNodeValue();
                        buildAllExtraMaps(m, code);
                        if (nodeCount < detailThreshold) {
                            bc = billableCodes(m);
                            if (bc.size() == 0) billability = 1;
                            if (bc.size() > 0) billability = 2;
                        }
                        break;
                    case "desc":
                        desc = m.getFirstChild().getNodeValue();
                        break;
                }
            }

            String exclude = "of in at other is are and or to from not on by" + query.toLowerCase();

            if (!code.equals("")) {
                seventh = null;

                for (int cl = code.length() - 1; cl >= 2; cl--) {
                    String csub = code.substring(0, cl);

                    if (SeventhCharRules.containsKey(csub)) {
                        seventh = SeventhCharRules.get(csub);
                        break;
                    }
                }

                String googleUrl = "https://google.com/search?q=" + URLEncoder.encode(desc, "UTF-8");
                String[] words = desc.split("[ \\t\\r\\n\\v\\f,;]");

                for (String w : words) {
                    w = w.toLowerCase().replace("(", "").replace(")", "")
                            .replace("[", "").replace("]", "");
                    if (resultSet.tags.contains(w)) continue;
                    if (exclude.contains(w)) continue;
                    resultSet.tags.add(w);
                }

                String desc2 =
                        locateExtra(code, CodeFirst, " <strong>First code</strong> ") +
                                locateExtra(code, CodeAlso, " <strong>Also, code</strong> ") +
                                locateExtra(code, Excludes1, " <strong>Excluding </strong>") +
                                locateExtra(code, Excludes2, " <strong>Consider instead </strong>") +
                                locateExtra(code, Includes, " <strong>Includes: </strong>") +
                                locateExtra(code, InclusionTerm, " <strong>Includes term(s) of </strong>") +
                                locateExtra(code, UseAdditionalCode, " <strong>Additionally  </strong>");



                if (seventh != null && code.length() > 3 && billability == 1) {
                    // Makes shorter codes to comply with full 7-place. If sub-code is
                    // not known, an X will be placed on lower digits.
                    for (String subCode : seventh.keySet()) {
                        CodeValue cv = new CodeValue();
                        String code2 = code;
                        while (code2.length() < 7) {
                            code2 = code2 + "X";
                        }
                        cv.icd10Code = code2 + subCode;
                        resultSet.subCodes.add(subCode);
                        cv.desc = boldKeywords(desc + ", " + seventh.get(subCode) +
                                ". " + desc2, query);
                        cv.url = googleUrl;
                        cv.billability = billability;
                        cv.billableCodes = bc;

                        codeDiscovery(cv);
                        codeValueList.add(cv);
                    }
                } else {
                    CodeValue cv = new CodeValue();
                    cv.icd10Code = code;
                    cv.desc = boldKeywords(desc + ". " + desc2, query);
                    cv.url = googleUrl;
                    codeDiscovery(cv);
                    cv.billability = billability;
                    cv.billableCodes = bc;
                    codeValueList.add(cv);
                }
            }
        }

        resultSet.codeValues = codeValueList;

        if (nodeCount < 2) resultSet.tags.clear();

        return resultSet;
    }

    public String boldKeywords(String desc, String keyword) {

        String res = desc;

        for(String kw: keyword.trim().split(" ")) {

            String ckw = kw.substring(0,1).toUpperCase() + kw.substring(1);
            res = res.replace(kw, "<b><i>"+ kw + "</i></b>");
            res = res.replace(ckw, "<b><i>"+ ckw + "</i></b>");
        }

        return res;

    }

    private void codeDiscovery(CodeValue cv) {

        Matcher match = icdCodePattern.matcher(cv.desc);

        while (match.find()) {
            String code = match.group();
            code = code.replace("(", "")
                    .replace(".-", "")
                    .replace(".- ", "")
                    .replace("-)","")
                    .replace(")", "");
            if (code.contains(",")) {
                String[] split = code.split(",");
                Collections.addAll(cv.foundCodes, split);
            } else {
                cv.foundCodes.add(code);
            }
        }
    }

    /**
     * Loads the 7th Character Rules to be used for lookups.
     *
     * @return The map of 7th rules per each code level
     * @throws Exception
     */
    private Map<String, Map<String, String>> load7thCharRules() throws Exception {

        Map<String, Map<String, String>> sevenCharRuleSet = new HashMap<>();
        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = "//sevenChrDef/..";
        NodeList nodeList = (NodeList) xpath.evaluate(expression, Icd10Doc, XPathConstants.NODESET);
        int ruleNodesCount = nodeList.getLength();

        for (int n = 0; n < ruleNodesCount; n++) {

            Node ruleParent = nodeList.item(n);
            NodeList nodes = ruleParent.getChildNodes();
            int nodesCount = nodes.getLength();
            String code = "";
            Map<String, String> seventh = new HashMap<>();

            for (int i = 0; i < nodesCount; i++) {
                Node node = nodes.item(i);
                String nodeName = node.getNodeName();

                if (nodeName.equals("name")) {
                    code = node.getFirstChild().getNodeValue();
                    // Logger.debug(code);
                } else if (nodeName.equals("sevenChrDef")) {
                    NodeList ruleLetters = node.getChildNodes();
                    for (int k = 0; k < ruleLetters.getLength(); k++) {
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

    private String locateExtra(String code, Map<String, String> map, String label) {
        int codeLen = code.length();
        String bigNote = "";
        for (int c = codeLen; c >= 3; c--) {
            if (c == 4) continue;
            String currentCode = code.substring(0, c);
            if (map.containsKey(currentCode)) {
                String note = map.get(currentCode);
                if (note == null) continue;
                bigNote = note + " " + bigNote;
            }
        }

        if (bigNote.equals("")) return "";
        return label + " " + bigNote;
    }

    /**
     * Builds the extra data maps like Excludes, Includes, CodeFirst and CodeTogether items
     * in the tree.
     *
     * @param codeNode The Xpath node containing the code
     * @param code     ICD-10 code for the note
     * @throws Exception
     */
    public void buildAllExtraMaps(Node codeNode, String code) throws Exception {

        buildExtraMap(Excludes1, codeNode, code, "excludes1");
        buildExtraMap(Excludes2, codeNode, code, "excludes2");
        buildExtraMap(Includes, codeNode, code, "includes");
        buildExtraMap(CodeFirst, codeNode, code, "codeFirst");
        buildExtraMap(UseAdditionalCode, codeNode, code, "useAdditionalCode");
        buildExtraMap(CodeAlso, codeNode, code, "codeAlso");
        buildExtraMap(InclusionTerm, codeNode, code, "inclusionTerm");
    }

    private void buildExtraMap(Map<String, String> map, Node codeNode, String code, String noteNodeName) {

        if (!map.containsKey(code)) {
            Node current = codeNode.getParentNode();
            int codeLen = code.length();

            for (int c = codeLen; c >= 3; c--) {
                if (c == 4) continue;
                String currentCode = code.substring(0, c);
                NodeList nodes = current.getChildNodes();
                String notes = "";

                for (int n = 0; n < nodes.getLength(); n++) {
                    Node m = nodes.item(n);
                    String s = m.getNodeName();
                    if (s.equals(noteNodeName)) {
                        notes = m.getTextContent();
                    }
                }

                if (!notes.equals("")) {
                    map.put(currentCode, notes);
                } else {
                    map.put(currentCode, null);
                }
                current = current.getParentNode();
            }
        }
    }

    public List<String> billableCodes(Node codeNode) throws Exception
    {
        List<String> b = new ArrayList<String>();

        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = "descendant::diag/name";

        NodeList nodeList = (NodeList) xpath.evaluate(expression, codeNode.getParentNode(), XPathConstants.NODESET);

        Logger.debug("nodes found {}", nodeList.getLength());
        int nodeCount = nodeList.getLength();

        for(int i = 0; i < nodeCount; i++) {
            b.add(nodeList.item(i).getFirstChild().getNodeValue());
        }

        return b;
    }
}
