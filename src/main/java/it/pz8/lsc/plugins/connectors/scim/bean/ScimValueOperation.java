package it.pz8.lsc.plugins.connectors.scim.bean;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Giuseppe Amato
 *
 */
public class ScimValueOperation extends ScimOperation implements Serializable {

    private static final long serialVersionUID = 4265211822672395149L;

    private Map<String, Object> pair;

    public ScimValueOperation(String op, String name, Object value)  {
        this.op = op;
        pair = new HashMap<>();
        pair.put(name, value);
    }

    public Map<String, Object> getValue() {
        return pair;
    }

}
