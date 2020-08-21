package it.pz8.lsc.plugins.connectors.scim.bean;

import java.util.Arrays;

/**
 * @author Giuseppe Amato
 *
 */
public enum OperationType {

    ADD("add"), 
    REPLACE("replace"), 
    REMOVE("remove");

    private final String name;

    private OperationType(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static OperationType getFromName(String name) {
        return Arrays.stream(OperationType.values())
                .filter(op -> op.name().toLowerCase().equals(name.toLowerCase()))
                .findFirst().orElse(null);
    }

}
