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
    }

    public Set<String> tags;
    public List<CodeValue> codeValues;
}
