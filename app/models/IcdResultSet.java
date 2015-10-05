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
                "bacterial infection wound laceration *noma inflamma* turtle shark ulcer water-ski";
        for(String t: base.split(" ")) {
            this.tags.add(t);
        }

        subCodes = new TreeSet<>();
    }

    public void clearCodesIfTooMany(int threshold) {
        if (codeValues.size() > threshold)
            codeValues.clear();
    }

    public Set<String> tags;
    public List<CodeValue> codeValues;
    public Set<String> subCodes;
}
