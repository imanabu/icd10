package models;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base code value data to be presented to the client
 * Created by manabu@wingumd.com on 10/2/15.
 */
public class CodeValue {
    public String icd10Code;
    public String desc;
    public String url;
    public Set<String> foundCodes = new HashSet<>();
    public List<String> billableCodes = new ArrayList<String>();
    public int billability = 0; // 0: not sure, 1: yes, 2: more codes
}
