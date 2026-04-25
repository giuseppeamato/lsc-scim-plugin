package it.pz8.lsc.plugins.connectors.scim.bean;

import java.io.Serializable;

/**
 * @author Giuseppe Amato
 *
 */
public class ValueTypeStruct implements Serializable {

	private static final long serialVersionUID = -1652454332956200324L;

	private String type;
    private String value;

    public ValueTypeStruct(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

}
