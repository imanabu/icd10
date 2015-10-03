package models;

import java.util.*;

/**
 * Created by manabutokunaga on 10/2/15.
 */
public class IcdResultSet {

    public IcdResultSet()
    {
        tags = new TreeSet<>();
        codeValues = new ArrayList<>();
        String base = "left right upper lower head eye nose ear neck shoulder chest abdomen back " +
                "proximal distal lateral bilateral posterior anterior buttock arm hand leg foot viral " +
                "bacterial infection wound laceration noma inflamma ulcer";
        for(String t: base.split(" ")) {
            this.tags.add(t);
        }
    }

    public Set<String> tags;
    public List<CodeValue> codeValues;
}
