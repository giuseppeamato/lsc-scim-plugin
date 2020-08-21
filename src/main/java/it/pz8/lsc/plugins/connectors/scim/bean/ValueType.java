package it.pz8.lsc.plugins.connectors.scim.bean;

/**
 * @author Giuseppe Amato
 *
 */
public class ValueType {

    private String type;
    private String value;

    public ValueType(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
