package models;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by manabutokunaga on 10/2/15.
 */
public class CodeValue {
    public String icd10Code;
    public String desc;
    public String url;
    public Set<String> foundCodes = new HashSet();
}
