package it.pz8.lsc.plugins.connectors.scim;

import static org.lsc.LscDatasetModification.LscDatasetModificationType.DELETE_VALUES;
import static org.lsc.LscDatasetModification.LscDatasetModificationType.REPLACE_VALUES;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

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
import it.pz8.lsc.plugins.connectors.scim.rs.BasicAuthenticator;
import it.pz8.lsc.plugins.connectors.scim.rs.ClientBuilderCustomizer;

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
    public static final String ATTRIBUTES_PARAM = "attributes";
    public static final String TYPE_ATTRIBUTE = "type";
    public static final String DISPLAY_ATTRIBUTE = "display";
    public static final String VALUE_ATTRIBUTE = "value";
    protected static final String[] MULTIVALUE_ATTRS_SELECTORS = {TYPE_ATTRIBUTE, DISPLAY_ATTRIBUTE};
    public static final String EQ_OPERATOR = " eq ";
    private static final String HTTP_STATUS_TPL_MSG = "status: %d, message: %s";
    private static final int PAGESIZE_DEFAULT_VALUE = 0;
    private static final Pattern MULTIVALUE_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ScimDao.class);
    private static final List<ClientBuilderCustomizer> CLIENT_CUSTOMIZERS = 
    		StreamSupport.stream(ServiceLoader.load(ClientBuilderCustomizer.class).spliterator(), false).toList();
    
    private final String entity;    
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
        this.pivot = getStringParameter(settings.getPivot());
        this.domain = getStringParameter(settings.getDomain());
        this.namespaces = settings.getSchema()!=null?settings.getSchema().getNamespace():new ArrayList<>();
        this.filter = getStringParameter(settings.getFilter()).map(this::replaceAllAliases);
        this.attributes = getStringParameter(settings.getAttributes()).map(this::replaceAllAliases);
        this.excludedAttributes = getStringParameter(settings.getExcludedAttributes()).map(this::replaceAllAliases);
        this.pageSize = Optional.ofNullable(settings.getPageSize()).filter(size -> size > 0);

        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true)
                .register(new BasicAuthenticator(connection.getUsername(), connection.getPassword()));
        CLIENT_CUSTOMIZERS.forEach(c -> c.customize(clientBuilder));

        Client client = clientBuilder.build();
        target = client.target(connection.getUrl());
    }

    private Optional<String> getStringParameter(String parameter) {
        return Optional.ofNullable(parameter).filter(currentfilter -> !currentfilter.trim().isEmpty());
    }

    public Map<String, LscDatasets> getList() throws LscServiceException {
        return getList(filter);
    }

    public String getPivotName() {
        return pivot.map(p -> p).orElse(ID);
    }

    public Map<String, LscDatasets> getList(Optional<String> computedFilter) throws LscServiceException {
        Map<String, LscDatasets> resources = new LinkedHashMap<>();
        String pivotName = getPivotName();
        int resultsPerPage = isIdFilter(computedFilter, pivotName) ? 0 : pageSize.orElse(PAGESIZE_DEFAULT_VALUE);
        int startIndex = 1;
        try {
            List<Map<String, Object>> page;
            do {
                WebTarget currentTarget = buildListTarget(computedFilter, pivotName, startIndex, resultsPerPage);
                page = fetchPage(currentTarget);
                page.forEach(resource -> resources.put(resource.get(pivotName).toString(), toDatasets(resource)));
                startIndex += resultsPerPage;
            } while (!page.isEmpty() && resultsPerPage > 0);
        } catch (JsonProcessingException e) {
            throw new LscServiceException(e);
        }
        return resources;
    }
    
    private boolean isIdFilter(Optional<String> filter, String pivotName) {
        return filter.filter(f -> f.contains(ID + "=") || f.contains(pivotName + "=")).isPresent();
    }
    
    private WebTarget buildListTarget(Optional<String> computedFilter, String pivotName, int startIndex, int resultsPerPage) {
        WebTarget currentTarget = target.path(entity);
        if (domain.isPresent()) {
            currentTarget = currentTarget.queryParam("domain", domain.get());
        }
        if (computedFilter.isPresent()) {
            currentTarget = currentTarget.queryParam("filter", computedFilter.get());
        }
        String pivotFetchedAttrs = pivotName.equalsIgnoreCase(ID) ? ID : ID + "," + pivotName;
        currentTarget = currentTarget.queryParam(ATTRIBUTES_PARAM, pivotFetchedAttrs);
        if (resultsPerPage > 0) {
        	currentTarget = currentTarget.queryParam("startIndex", startIndex).queryParam("count", resultsPerPage);
        }
        LOGGER.debug("Retrieve {} list from: {} - startIndex: {} - pageSize: {} ", entity, currentTarget.getUri(), startIndex, resultsPerPage);
        return currentTarget;
    }
    
    private List<Map<String, Object>> fetchPage(WebTarget currentTarget) throws LscServiceException, JsonProcessingException {
        Response response = currentTarget.request().accept(MediaType.APPLICATION_JSON).get(Response.class);
        try {
            if (!checkResponse(response)) {
                String errorMessage = String.format(HTTP_STATUS_TPL_MSG, response.getStatus(), response.readEntity(String.class));
                LOGGER.error(errorMessage);
                throw new LscServiceException(errorMessage);
            }
            Map<String, Object> results = mapper.readValue(response.readEntity(String.class), LinkedHashMap.class);
            if (results != null && results.get(RESOURCES) != null) {
                return (List<Map<String, Object>>) results.get(RESOURCES);
            }
            return List.of();
        } finally {
            response.close();
        }
    }
    
    private LscDatasets toDatasets(Map<String, Object> resource) {
        LscDatasets datasets = new LscDatasets();
        datasets.put(ID, resource.get(ID));
        pivot.ifPresent(p -> datasets.put(p, resource.get(p)));
        return datasets;
    }
    
    public Map<String, Object> getDetails(String id) {
        Response response = null;
        try {
            WebTarget currentTarget = target.path(entity).path(id);
            if (attributes.isPresent()) {
                currentTarget = currentTarget.queryParam(ATTRIBUTES_PARAM, attributes.get());
            }
            if (excludedAttributes.isPresent()) {
                currentTarget = currentTarget.queryParam("excludedAttributes", excludedAttributes.get());
            }
            LOGGER.debug("Retrieve {} detail from: {} ", getEntityName(), currentTarget.getUri());
            response = currentTarget.request().accept(MediaType.APPLICATION_JSON).get(Response.class);
            if (!checkResponse(response)) {
                if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(String.format("%s %s cannot be found", getEntityName(), id));
                }                
                String errorMessage = String.format(HTTP_STATUS_TPL_MSG, response.getStatus(), response.readEntity(String.class));
                LOGGER.error(errorMessage);
                throw new ProcessingException(errorMessage);
            }
            Map<String, Object> detail = flatten(response.readEntity(String.class));
            LOGGER.debug("Details :\n{}", detail);
            return detail;           
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
                currentTarget = currentTarget.queryParam(ATTRIBUTES_PARAM, attributes.get());
            }
            if (excludedAttributes.isPresent()) {
                currentTarget = currentTarget.queryParam("excludedAttributes", excludedAttributes.get());
            }
            LOGGER.debug("Retrieve {} detail from: {} ", getEntityName(), currentTarget.getUri());
            response = currentTarget.request().accept(MediaType.APPLICATION_JSON).get(Response.class);
            if (!checkResponse(response)) {              
                String errorMessage = String.format(HTTP_STATUS_TPL_MSG, response.getStatus(), response.readEntity(String.class));
                LOGGER.error(errorMessage);
                throw new ProcessingException(errorMessage);
            }
            Map<String, Object> results = mapper.readValue(response.readEntity(String.class), LinkedHashMap.class);
            LOGGER.debug("SCIM Response :\n{}", results);
            if (results!=null && results.get(RESOURCES)!=null) {
                List<Map<String, Object>> resourcesMap = (List<Map<String, Object>>)results.get(RESOURCES);
                switch (resourcesMap.size()) {
                case 0:
                    throw new NotFoundException(String.format("%s %s cannot be found by pivot", getEntityName(), pivotValue));
                case 1:
                    detail = flatten(mapper.writeValueAsString(resourcesMap.get(0)));
                    break;
                default:
                    throw new LscServiceException(String.format("Multiple results for %s %s", getEntityName(), pivotValue));
                }
            } else {
                throw new NotFoundException(String.format("%s %s no results found", getEntityName(), pivotValue));
            }
            LOGGER.debug("Details :\n{}", detail);
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

    public boolean create(LscModifications lm) {
        Response response = null;
        boolean result = false;
        try {
            WebTarget currentTarget = target.path(entity);
            LOGGER.debug("Create {} in: {} \n[{}]", getEntityName(), currentTarget.getUri(), lm);
            Map<String, Object> entityattributes = new HashMap<>();
            List<LscDatasetModification> diffs = lm.getLscAttributeModifications();
            entityattributes.put(SCHEMAS, new ArrayList<String>());
            for (LscDatasetModification attributeModification : diffs) {
                if (isMultivaluedAttribute(attributeModification.getAttributeName())) {
                    String attrName = getMultivaluedAttributeName(attributeModification.getAttributeName());
                    String attrIdx = getMultivaluedAttributeIndex(attributeModification.getAttributeName());
                    List<Object> multivalues =  (List<Object>)Optional.ofNullable(entityattributes.get(attrName)).orElse(new ArrayList<Object>());
                    if (StringUtils.isBlank(attrIdx)) {
                        multivalues.addAll(attributeModification.getValues());
                    } else {
                        multivalues.add(new ValueType(StringUtils.substringAfter(attrIdx, TYPE_ATTRIBUTE+EQ_OPERATOR), getFirstValueAsString(attributeModification.getValues())));
                    }
                    entityattributes.put(attrName, multivalues);
                } else {
                    entityattributes.put(attributeModification.getAttributeName(), getFirstValueAsString(attributeModification.getValues()));
                }
            }
            String unflattenDiffs = unflatten(entityattributes);
            LOGGER.debug("SCIM payload: \n{}", unflattenDiffs);
            response = currentTarget.request(MediaType.APPLICATION_JSON_TYPE).post(Entity.json(unflattenDiffs));
            if (!checkResponse(response)) {
            	response.bufferEntity();
            	String body = response.hasEntity() ? response.readEntity(String.class) : "<empty>";
                LOGGER.error("Error {} ({}) while creating {}\r\n{}",  response.getStatus(), response.getStatusInfo(), getEntityName(), body);
            } else {
                result = true;
            }
        } catch (Exception e) {
            LOGGER.error("Error {} while creating {}: {}", e.getMessage(), getEntityName(), lm);
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
            LOGGER.debug("Update {} in: {} ", getEntityName(), currentTarget.getUri());
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
                case UNKNOWN:
                    break;
                }
                if (operation!=null) {
                    ScimPathOperation op = createOperation(operation, diff, lm);
                    patchOp.addOperations(op);
                }
            }
            if (!patchOp.getOperations().isEmpty()) {
                String patchOpJson = mapper.writeValueAsString(patchOp);
                LOGGER.debug("SCIM payload: {}", patchOpJson);
                response = currentTarget.request(MediaType.APPLICATION_JSON_TYPE).method(HttpMethod.PATCH, Entity.entity(patchOpJson, MediaType.APPLICATION_JSON));
                if (!checkResponse(response)) {
                    LOGGER.error("Error {} ({}) while creating {} {}",  response.getStatus(), response.getStatusInfo(), getEntityName(), lm.getMainIdentifier());
                } else {
                    result = true;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error {} while updating {}: {}", e.getMessage(), getEntityName(), lm);
            return false;
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return result;
    }

    private ScimPathOperation createOperation(String operation, LscDatasetModification diff, LscModifications lm) {
        String path = replaceAlias(diff.getAttributeName());
        Serializable value = getFirstValueAsString(diff.getValues());
        if (isMultivaluedAttribute(diff.getAttributeName()) && !diff.getOperation().equals(DELETE_VALUES)) {
            path = getMultivaluedAttributeName(diff.getAttributeName());
            String attrIdx = getMultivaluedAttributeIndex(diff.getAttributeName());
            if (StringUtils.isBlank(attrIdx)) {
                // Simple multivalue
                value = stringValuesToJsonValues(diff.getValues());
            } else {
                // Multivalue with path
                if (hasValue(lm.getDestinationBean(), diff.getAttributeName())) {
                    path = (!diff.getOperation().equals(REPLACE_VALUES))?path:replaceAlias(diff.getAttributeName()).concat(".").concat(VALUE_ATTRIBUTE);
                    value = getFirstValueAsString(diff.getValues());
                    operation = OperationType.REPLACE.getName();
                } else {
                    value = new ArrayList<Serializable>();
                    ((List<Serializable>)value).add(new ValueType(StringUtils.substringAfter(attrIdx, TYPE_ATTRIBUTE+EQ_OPERATOR), getFirstValueAsString(diff.getValues())));
                    operation = OperationType.ADD.getName();
                }
            }
        }
        LOGGER.debug("op: {}, name: {}, value: {}", diff.getOperation(), path, value);
        return new ScimPathOperation(operation, path, (!operation.equals(OperationType.REMOVE.getName()))?value:null);
    }

    public boolean delete(String pivotValue) throws LscServiceException {
        Response response = null;
        boolean result = false;
        try {
            String id = getPivotName().equalsIgnoreCase(ID)?pivotValue:findFirstByPivot(pivotValue)
                    .map(entry -> entry.getValue().getStringValueAttribute(ID)).orElseThrow(() -> new LscServiceException("ID not found"));
            WebTarget currentTarget = target.path(entity).path(id);
            LOGGER.debug("Delete {} from: {} ", getEntityName(), currentTarget.getUri());
            response = currentTarget.request(MediaType.APPLICATION_JSON_TYPE).delete();
            if (!checkResponse(response)) {
                LOGGER.error(String.format(HTTP_STATUS_TPL_MSG, response.getStatus(), response.readEntity(String.class)));
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
        pivotFilter.append(getPivotName()).append(EQ_OPERATOR).append(pivotValue.replace("'", "''"));
        return filter.map(f -> f + " and " + pivotFilter.toString()).orElse(pivotFilter.toString());
    }
    
    private String getFirstValueAsString(List<Object> valuesList) {
        return Optional.ofNullable(valuesList)
            .filter(values -> !values.isEmpty())
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
        Matcher m = MULTIVALUE_PATTERN.matcher(attributeName);
        return m.find() ? m.group(1):null;
    } 
    
    /**
     * Converts a structured json string into a flat map. 
     * It also replaces aliases of extension schemas defined in the configuration file.
     */
    private Map<String, Object> flatten(String jsonAttributes) {
        String jsonAttrsWithSchemaAlias = jsonAttributes;
        for (NamespaceType namespace : namespaces) {
            jsonAttrsWithSchemaAlias = StringUtils.replace(jsonAttrsWithSchemaAlias, namespace.getUri(), namespace.getAlias());    
        }
        Map<String, Object> flattenDiffs = JsonFlattener.flattenAsMap(jsonAttrsWithSchemaAlias);
        return processFlatDiffs(flattenDiffs);
    }
    
    /**
     * Processes the flat map to obtain a single entry per type for each multivalued attribute
     */
    private Map<String, Object> processFlatDiffs(Map<String, Object> flattenDiffs) {
        List<String> types = flattenDiffs.keySet().stream()
                .filter(key -> ArrayUtils.contains(MULTIVALUE_ATTRS_SELECTORS, StringUtils.substringAfter(key, "].")) || key.endsWith("]") )
                .toList();
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
     * e.g.: "ENTERPRISE_USER_SCHEMA.department" become "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department" 
     */
    private String replaceAlias(String attributeName) {
        return namespaces.stream()
                .filter(entry -> attributeName.startsWith(entry.getAlias()))
                .findFirst()
                .map(ns -> StringUtils.replace(attributeName, ns.getAlias()+".", ns.getUri()+":"))
                .orElse(attributeName);
    }

    /**
     * Converts all aliases in the given input string.
     * Intended for use in filter and attribute lists. 
     */
    private String replaceAllAliases(String input) {
        String result = input;
        for (NamespaceType ns : namespaces) {
            result = StringUtils.replace(result, ns.getAlias(), ns.getUri());
        }
        return result;
    }
    
    private boolean hasValue(IBean bean, String attrName) {
        Set<Object> currentDestValue = bean.getDatasetById(attrName);
        return (currentDestValue!=null && !currentDestValue.isEmpty());
    }
    
    private ArrayList<Object> stringValuesToJsonValues(List<Object> stringValues) {
        ArrayList<Object> jsonValues = new ArrayList<>();
        for (Object entry : stringValues) {
            if (!entry.toString().isEmpty()) {
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
