package it.pz8.lsc.plugins.connectors.scim;

import static org.lsc.LscDatasetModification.LscDatasetModificationType.DELETE_VALUES;
import static org.lsc.LscDatasetModification.LscDatasetModificationType.REPLACE_VALUES;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.lsc.LscDatasetModification;
import org.lsc.LscDatasets;
import org.lsc.LscModifications;
import org.lsc.beans.IBean;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.exception.LscServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.JsonFlattener;
import com.github.wnameless.json.unflattener.JsonUnflattener;

import it.pz8.lsc.plugins.connectors.scim.bean.OperationType;
import it.pz8.lsc.plugins.connectors.scim.bean.ScimPatchResource;
import it.pz8.lsc.plugins.connectors.scim.bean.ScimPathOperation;
import it.pz8.lsc.plugins.connectors.scim.bean.ValueType;
import it.pz8.lsc.plugins.connectors.scim.generated.NamespaceType;
import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;

/**
 * @author Giuseppe Amato
 *
 */
public class ScimDao {

    public static final String USERS = "Users";
    public static final String GROUPS = "Groups";
    public static final String RESOURCES = "Resources";
    public static final String SCHEMAS = "schemas";
    public static final String ID = "id";
    public static final String TYPE_ATTRIBUTE = "type";
    public static final String DISPLAY_ATTRIBUTE = "display";
    public static final String VALUE_ATTRIBUTE = "value";
    public static final String[] MULTIVALUE_ATTRS_SELECTORS = {TYPE_ATTRIBUTE, DISPLAY_ATTRIBUTE};
    public static final String EQ_OPERATOR = " eq ";

    private static final Logger LOGGER = LoggerFactory.getLogger(ScimDao.class);

    private final String entity;
    @Deprecated
    private final Optional<String> sourcePivot;
    private final Optional<String> pivot;
    private final Optional<String> domain;
    private final Optional<Integer> pageSize;
    private final Optional<String> filter;
    private final Optional<String> attributes;
    private final Optional<String> excludedAttributes;
    private final List<NamespaceType> namespaces;
    
    private WebTarget target; 
    private ObjectMapper mapper;

    public ScimDao(PluginConnectionType connection, ScimServiceSettings settings) {
        LOGGER.debug("Init service");
        mapper = new ObjectMapper();
        this.entity = settings.getEntity();
        this.sourcePivot = getStringParameter(settings.getSourcePivot());
        this.pivot = getStringParameter(settings.getPivot());
        this.domain = getStringParameter(settings.getDomain());
        this.filter = getStringParameter(settings.getFilter());
        this.attributes = getStringParameter(settings.getAttributes());
        this.excludedAttributes = getStringParameter(settings.getExcludedAttributes());
        this.pageSize = Optional.ofNullable(settings.getPageSize()).filter(size -> size > 0);
        this.namespaces = settings.getSchema()!=null?settings.getSchema().getNamespace():new ArrayList<NamespaceType>();
        
        Client client = ClientBuilder.newClient().property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true)
                .register(new BasicAuthenticator(connection.getUsername() , connection.getPassword()));
        target = client.target(connection.getUrl());
    }

    private Optional<String> getStringParameter(String parameter) {
        return Optional.ofNullable(parameter).filter(filter -> !filter.trim().isEmpty());
    }

    public Map<String, LscDatasets> getList() throws LscServiceException {
        return getList(filter);
    }

    /**
     * @deprecated
     * Next release will relies on pivotTransformation feature of LSC 2.2
     */
    @Deprecated
    public String getSourcePivotName() {
        return sourcePivot.map(p -> p).orElse(getPivotName());
    }
    
    public String getPivotName() {
        return pivot.map(p -> p).orElse(ID);
    }

    public Map<String, LscDatasets> getList(Optional<String> computedFilter) throws LscServiceException {
        Map<String, LscDatasets> resources = new LinkedHashMap<String, LscDatasets>();
        Response response = null;
        try {
            WebTarget currentTarget = target.path(entity);
            if (domain.isPresent()) {
                currentTarget = currentTarget.queryParam("domain", domain.get());
            }
            if (computedFilter.isPresent()) {
                currentTarget = currentTarget.queryParam("filter", computedFilter.get());
            }
            if (pageSize.isPresent()) {
                currentTarget = currentTarget.queryParam("startIndex", 1);
            }
            if (pageSize.isPresent()) {
                currentTarget = currentTarget.queryParam("count", pageSize.get());
            }
            String pivotName = getPivotName();
            String pivotFetchedAttrs = pivotName.equalsIgnoreCase(ID) ? ID : ID + "," + pivotName;
            currentTarget = currentTarget.queryParam("attributes", pivotFetchedAttrs);
            LOGGER.debug(String.format("Retrieve %s list from: %s ", entity, currentTarget.getUri().toString()));
            response = currentTarget.request().accept(MediaType.APPLICATION_JSON).get(Response.class);
            if (!checkResponse(response)) {
                String errorMessage = String.format("status: %d, message: %s", response.getStatus(), response.readEntity(String.class));
                LOGGER.error(errorMessage);
                throw new LscServiceException(errorMessage);
            }
            Map<String, Object> results = mapper.readValue(response.readEntity(String.class), Map.class);
            if (results!=null && results.get(RESOURCES)!=null) {
                List<Map> resourcesMap = (List)results.get(RESOURCES);
                for (Map resource : resourcesMap) {
                    LscDatasets datasets = new LscDatasets();
                    datasets.put(ID, resource.get(ID));
                    pivot.ifPresent(p -> datasets.put(p, resource.get(p)));
                    resources.put(resource.get(pivotName).toString(), datasets);
                }
            }
        } catch (JsonProcessingException e) {
            throw new LscServiceException(e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return resources;
    }

    public Map<String, Object> getDetails(String id) throws LscServiceException {
        Response response = null;
        try {
            WebTarget currentTarget = target.path(entity).path(id);
            if (attributes.isPresent()) {
                currentTarget = currentTarget.queryParam("attributes", attributes.get());
            }
            if (excludedAttributes.isPresent()) {
                currentTarget = currentTarget.queryParam("excludedAttributes", excludedAttributes.get());
            }
            LOGGER.debug(String.format("Retrieve %s detail from: %s ", getEntityName(), currentTarget.getUri().toString()));
            response = currentTarget.request().accept(MediaType.APPLICATION_JSON).get(Response.class);
            if (!checkResponse(response)) {
                if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(String.format("%s %s cannot be found", getEntityName(), id));
                }                
                String errorMessage = String.format("status: %d, message: %s", response.getStatus(), response.readEntity(String.class));
                LOGGER.error(errorMessage);
                throw new ProcessingException(errorMessage);
            }
            Map<String, Object> detail = flatten(response.readEntity(String.class));
            LOGGER.debug(String.format("Details :\r\n%s", detail));
            return detail;
        } catch (JsonProcessingException e) {
            throw new LscServiceException(e);            
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }
    
    public Map<String, Object> getDetailsByPivot(String pivotValue) throws LscServiceException {
        Response response = null;
        Map<String, Object> detail = null;
        try {
            WebTarget currentTarget = target.path(entity);
            currentTarget = currentTarget.queryParam("filter", buildPivotFilter(pivotValue));
            if (attributes.isPresent()) {
                currentTarget = currentTarget.queryParam("attributes", attributes.get());
            }
            if (excludedAttributes.isPresent()) {
                currentTarget = currentTarget.queryParam("excludedAttributes", excludedAttributes.get());
            }
            LOGGER.debug(String.format("Retrieve %s detail from: %s ", getEntityName(), currentTarget.getUri().toString()));
            response = currentTarget.request().accept(MediaType.APPLICATION_JSON).get(Response.class);
            if (!checkResponse(response)) {              
                String errorMessage = String.format("status: %d, message: %s", response.getStatus(), response.readEntity(String.class));
                LOGGER.error(errorMessage);
                throw new ProcessingException(errorMessage);
            }
            Map<String, Object> results = mapper.readValue(response.readEntity(String.class), Map.class);
            LOGGER.debug(String.format("SCIM Response :\r\n%s", results));
            if (results!=null && results.get(RESOURCES)!=null) {
                List<Map> resourcesMap = (List)results.get(RESOURCES);
                switch (resourcesMap.size()) {
                case 0:
                    throw new NotFoundException(String.format("%s %s cannot be found", getEntityName(), pivotValue));
                case 1:
                    detail = flatten(mapper.writeValueAsString(resourcesMap.get(0)));
                    break;
                default:
                    throw new LscServiceException(String.format("Multiple results for %s %s", getEntityName(), pivotValue));
                }
            } else {
                throw new NotFoundException(String.format("%s %s cannot be found", getEntityName(), pivotValue));
            }
            LOGGER.debug(String.format("Details :\r\n%s", detail));
        } catch (JsonProcessingException e) {
            throw new LscServiceException(e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return detail;
    }

    public Optional<Entry<String, LscDatasets>> findFirstByPivot(String pivotValue) throws LscServiceException {
        return getList(Optional.of(buildPivotFilter(pivotValue))).entrySet().stream().findFirst();
    }

    public boolean create(LscModifications lm) throws LscServiceException {
        Response response = null;
        boolean result = false;
        try {
            WebTarget currentTarget = target.path(entity);
            LOGGER.debug(String.format("Create %s in: %s \r\n[%s]", getEntityName(), currentTarget.getUri().toString(), lm));
            Map<String, Object> attributes = new HashMap<>();
            List<LscDatasetModification> diffs = lm.getLscAttributeModifications();
            attributes.put(SCHEMAS, new ArrayList<String>());
            for (LscDatasetModification attributeModification : diffs) {
                if (isMultivaluedAttribute(attributeModification.getAttributeName())) {
                    String attrName = getMultivaluedAttributeName(attributeModification.getAttributeName());
                    String attrIdx = getMultivaluedAttributeIndex(attributeModification.getAttributeName());
                    List<Object> multivalues =  (List<Object>)Optional.ofNullable(attributes.get(attrName)).orElse(new ArrayList());
                    if (StringUtils.isBlank(attrIdx)) {
                        multivalues.addAll(attributeModification.getValues());
                    } else {
                        multivalues.add(new ValueType(StringUtils.substringAfter(attrIdx, TYPE_ATTRIBUTE+EQ_OPERATOR), getFirstValueAsString(attributeModification.getValues())));
                    }
                    attributes.put(attrName, multivalues);
                } else {
                    attributes.put(attributeModification.getAttributeName(), getFirstValueAsString(attributeModification.getValues()));
                }
            }
            String unflattenDiffs = unflatten(attributes);
            LOGGER.debug("SCIM payload: \r\n"+unflattenDiffs);
            response = currentTarget.request(MediaType.APPLICATION_JSON_TYPE).post(Entity.json(unflattenDiffs));
            if (!checkResponse(response)) {
                LOGGER.error(String.format("Error %d (%s) while creating %s", response.getStatus(), response.getStatusInfo(), getEntityName()));
            } else {
                result = true;
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Error %s while creating %s: %s", e.getMessage(), getEntityName(), lm));
            return false;
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return result;
    }

    public boolean update(LscModifications lm) throws LscServiceException {
        Response response = null;
        boolean result = false;
        try {
            String id = getPivotName().equalsIgnoreCase(ID)?lm.getMainIdentifier():findFirstByPivot(lm.getMainIdentifier())
                    .map(entry -> entry.getValue().getStringValueAttribute(ID)).orElseThrow(() -> new LscServiceException("ID not found"));
            WebTarget currentTarget = target.path(entity).path(id);
            LOGGER.debug(String.format("Update %s in: %s ", getEntityName(), currentTarget.getUri().toString()));
            ScimPatchResource patchOp = new ScimPatchResource();
            List<LscDatasetModification> diffs = lm.getLscAttributeModifications();
            for (LscDatasetModification diff : diffs) {
                String operation = null; 
                switch (diff.getOperation()) {
                case DELETE_VALUES:
                    operation = OperationType.REMOVE.getName();
                    break;
                case ADD_VALUES:
                    operation = OperationType.ADD.getName();
                    break;
                case REPLACE_VALUES:
                    operation = OperationType.REPLACE.getName();
                    break;
                }
                String path = replaceAlias(diff.getAttributeName());
                Object value = getFirstValueAsString(diff.getValues());
                if (isMultivaluedAttribute(diff.getAttributeName()) && !diff.getOperation().equals(DELETE_VALUES)) {
                    path = getMultivaluedAttributeName(diff.getAttributeName());
                    String attrIdx = getMultivaluedAttributeIndex(diff.getAttributeName());
                    if (StringUtils.isBlank(attrIdx)) {
                        // Simple multivalue
                        value = stringValuesToJsonValues(diff.getValues());
                    } else {
                        // Complex multivalue
                        if (hasValue(lm.getDestinationBean(), diff.getAttributeName())) {
                            path = (!diff.getOperation().equals(REPLACE_VALUES))?path:replaceAlias(diff.getAttributeName()).concat(".").concat(VALUE_ATTRIBUTE);
                            value = getFirstValueAsString(diff.getValues());
                            operation = OperationType.REPLACE.getName();
                        } else {
                            value = new ArrayList<>();
                            ((List<Object>)value).add(new ValueType(StringUtils.substringAfter(attrIdx, TYPE_ATTRIBUTE+EQ_OPERATOR), getFirstValueAsString(diff.getValues())));
                            operation = OperationType.ADD.getName();
                        }
                    }
                }
                LOGGER.debug(String.format("op: %s, name: %s, value: %s", diff.getOperation(), path, value));
                ScimPathOperation op = new ScimPathOperation(operation, path, (!operation.equals(OperationType.REMOVE.getName()))?value:null);
                patchOp.addOperations(op);
            }
            if (patchOp.getOperations().size()>0) {
                String patchOpJson = mapper.writeValueAsString(patchOp);
                LOGGER.debug("SCIM payload:" + patchOpJson);
                response = currentTarget.request(MediaType.APPLICATION_JSON_TYPE).method(HttpMethod.PATCH, Entity.entity(patchOpJson, MediaType.APPLICATION_JSON));
                if (!checkResponse(response)) {
                    LOGGER.error(String.format("Error %d (%s) while updating %s: %s", response.getStatus(), response.getStatusInfo(), getEntityName(), lm.getMainIdentifier()));
                } else {
                    result = true;
                }
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Error %s while updating %s: %s", e.getMessage(), getEntityName(), lm));
            return false;
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return result;
    }

    public boolean delete(String pivotValue) throws LscServiceException {
        Response response = null;
        boolean result = false;
        try {
            String id = getPivotName().equalsIgnoreCase(ID)?pivotValue:findFirstByPivot(pivotValue)
                    .map(entry -> entry.getValue().getStringValueAttribute(ID)).orElseThrow(() -> new LscServiceException("ID not found"));
            WebTarget currentTarget = target.path(entity).path(id);
            LOGGER.debug(String.format("Delete %s from: %s ", getEntityName(), currentTarget.getUri().toString()));
            response = currentTarget.request(MediaType.APPLICATION_JSON_TYPE).delete();
            if (!checkResponse(response)) {
                String errorMessage = String.format("status: %d, message: %s", response.getStatus(), response.readEntity(String.class));
                LOGGER.error(errorMessage);
            } else {
                result = true;
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Error %s while creating %s: %s", e.getMessage(), getEntityName(), pivotValue));
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return result;
    }

    private static boolean checkResponse(Response response) {
        return Response.Status.Family.familyOf(response.getStatus()) == Response.Status.Family.SUCCESSFUL;
    }
    
    private String buildPivotFilter(String pivotValue) {
        StringBuilder pivotFilter = new StringBuilder();
        pivotFilter.append(getPivotName()).append(EQ_OPERATOR).append(pivotValue.replaceAll("'", "''"));
        return filter.map(f -> f + " and " + pivotFilter.toString()).orElse(pivotFilter.toString());
    }
    
    private String getFirstValueAsString(List<Object> valuesList) {
        return Optional.ofNullable(valuesList)
            .filter(values -> values.size() > 0)
            .map(List::iterator)
            .map(Iterator::next)
            .map(String::valueOf)
            .orElse(null);
    }
    
    private String getEntityName() {
        return entity.equals(USERS)?"user":"group";
    }
    
    /**
     * If the attribute is multivalued (the name contains square brackets) returns true.  
     */
    private boolean isMultivaluedAttribute(String attributeName) {
        return StringUtils.contains(attributeName, "[");
    }
    
    /**
     * Returns the attribute name without square brackets  
     */
    private String getMultivaluedAttributeName(String attributeName) {
        return StringUtils.substringBefore(attributeName, "[");
    }    
    
    /**
     * Returns the path of the multivalued attribute (the value contained into square brackets).
     * If the attribute is not multivalued, null is returned.
     */
    private String getMultivaluedAttributeIndex(String attributeName) {
        String attrType = null;
        Pattern p = Pattern.compile("\\[([^\\]]+)\\]");
        Matcher m = p.matcher(attributeName);
        if (m.find()) {
            attrType = m.group(1);
        }
        return attrType;
    } 
    
    /**
     * Converts a structured json string into a flat map. 
     * It also replaces aliases of extension schemas defined in the configuration file.
     */
    private Map<String, Object> flatten(String jsonAttributes) throws JsonProcessingException {
        String jsonAttrsWithSchemaAlias = jsonAttributes;
        for (NamespaceType namespace : namespaces) {
            jsonAttrsWithSchemaAlias = StringUtils.replace(jsonAttrsWithSchemaAlias, namespace.getUri(), namespace.getAlias());    
        }
        Map<String, Object> flattenDiffs = JsonFlattener.flattenAsMap(jsonAttrsWithSchemaAlias);
        flattenDiffs = processFlatDiffs(flattenDiffs);    
        return flattenDiffs;
    }
    
    /**
     * Processes the flat map to obtain a single entry per type for each multivalued attribute
     */
    private Map<String, Object> processFlatDiffs(Map<String, Object> flattenDiffs) {
        List<String> types = flattenDiffs.keySet().stream()
                .filter(key -> ArrayUtils.contains(MULTIVALUE_ATTRS_SELECTORS, StringUtils.substringAfter(key, "].")) || key.endsWith("]") )
                .collect(Collectors.toList());
        for (String key : types) {
            if (key.endsWith("]")) {
                String newKey = getMultivaluedAttributeName(key)+"[]";
                flattenDiffs.put(newKey, flattenDiffs.get(key));
                flattenDiffs.remove(key);
            } else {
                String type = (String)flattenDiffs.get(key);
                String selector = StringUtils.substringAfter(key, "].");
                String attrIndex = getMultivaluedAttributeIndex(key);
                String newKey = String.format("%s[%s%s%s]", getMultivaluedAttributeName(key), selector, EQ_OPERATOR, type);
                String valueKey = String.format("%s[%s].%s", getMultivaluedAttributeName(key), attrIndex, VALUE_ATTRIBUTE);
                flattenDiffs.put(newKey, flattenDiffs.get(valueKey));
                flattenDiffs.remove(key);
                flattenDiffs.remove(valueKey);
            }
        }
        return flattenDiffs;
    }
    
    /**
     * Converts flat map into a structured json string. 
     * It also replaces aliases of extension schemas defined in the configuration file.
     */
    private String unflatten(Map<String, Object> attributes) throws JsonProcessingException {
        String unflattenDiffs = JsonUnflattener.unflatten(mapper.writeValueAsString(attributes));
        for (NamespaceType namespace : namespaces) {
            unflattenDiffs = StringUtils.replace(unflattenDiffs, namespace.getAlias(), namespace.getUri());    
        }
        return unflattenDiffs;
    }

    /**
     * Converts attribute path with extension schema, replacing "." with ":".
     * e.g.: "ENTERPRISE_USER.department" become "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department" 
     */
    private String replaceAlias(String attributeName) throws JsonProcessingException {
        return namespaces.stream()
                .filter(entry -> attributeName.startsWith(entry.getAlias()))
                .findFirst()
                .map(ns -> StringUtils.replace(attributeName, ns.getAlias()+".", ns.getUri()+":"))
                .orElse(attributeName);
    }

    private boolean hasValue(IBean bean, String attrName) {
        Set<Object> currentDestValue = bean.getDatasetById(attrName);
        return (currentDestValue!=null && currentDestValue.size()>0);
    }
    
    private List<Object> stringValuesToJsonValues(List<Object> stringValues) {
        List<Object> jsonValues = new ArrayList<Object>();
        for (Object entry : stringValues) {
            if (!entry.toString().equals("")) {
                try {
                    jsonValues.add(mapper.readTree(entry.toString()));
                } catch (Exception e) {
                    jsonValues.add(entry.toString());
                }
            }
        }
        return jsonValues;
    }

}
