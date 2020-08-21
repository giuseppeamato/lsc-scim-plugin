package it.pz8.lsc.plugins.connectors.scim.bean;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Giuseppe Amato
 *
 */
public class ScimPatchResource {

    public static final String SCHEMA_PATCHOP = "urn:ietf:params:scim:api:messages:2.0:PatchOp";

    private List<String> schemas;
    @JsonInclude(Include.NON_NULL)
    private List<ScimOperation> operations;

    public ScimPatchResource() {
        schemas = new ArrayList<>();
        schemas.add(SCHEMA_PATCHOP);
        operations = new ArrayList<>();
    }

    @JsonInclude(Include.NON_NULL)
    @JsonProperty("Operations")
    public List<ScimOperation> getOperations() {
        return operations;
    }

    public void addOperations(ScimOperation operation) {
        this.operations.add(operation);
    }

    public List<String> getSchemas() {
        return schemas;
    }

}
