package models;

import java.util.*;

/**
 * The full set of search results to be presented to the client
 * Created by manabu@wingumd.com on 10/2/15.
 */
public class IcdResultSet {

    public IcdResultSet()
    {
        tags = new TreeSet<>();
        codeValues = new ArrayList<>();
        String base = "";
        Collections.addAll(this.tags, base.split(" "));

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
