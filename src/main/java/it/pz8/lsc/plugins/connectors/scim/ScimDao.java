package it.pz8.lsc.plugins.connectors.scim;

import static org.lsc.LscDatasetModification.LscDatasetModificationType.DELETE_VALUES;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.lsc.LscDatasetModification;
import org.lsc.LscDatasets;
import org.lsc.LscModifications;
import org.lsc.beans.IBean;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.ValuesType;
import org.lsc.exception.LscServiceConfigurationException;
import org.lsc.exception.LscServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.FlattenMode;
import com.github.wnameless.json.flattener.JsonFlattener;
import com.github.wnameless.json.unflattener.JsonUnflattener;

import it.pz8.lsc.plugins.connectors.scim.bean.OperationType;
import it.pz8.lsc.plugins.connectors.scim.bean.ScimPatchResource;
import it.pz8.lsc.plugins.connectors.scim.bean.ScimPathOperation;
import it.pz8.lsc.plugins.connectors.scim.bean.ScimSelector;
import it.pz8.lsc.plugins.connectors.scim.generated.FlatMultivalueStrategyType;
import it.pz8.lsc.plugins.connectors.scim.generated.NamespaceType;
import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;
import it.pz8.lsc.plugins.connectors.scim.rs.AuthClientBuilder;

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
    public static final String EQ_OPERATOR = " eq ";
    private static final String HTTP_STATUS_TPL_MSG = "status: %d, message: %s";
    private static final int PAGESIZE_DEFAULT_VALUE = 0;
    private static final Pattern OBJECT_ARRAY_KEY = Pattern.compile("^(.+)\\[(\\d+)\\]\\.(.+)$");
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ScimDao.class);

    private final String entity;
    private final Optional<String> sourcePivot;
    private final Optional<String> pivot;
    private final Optional<String> domain;
    private final Optional<Integer> pageSize;
    private final Optional<String> filter;
    private final Optional<String> attributes;
    private final Optional<String> excludedAttributes;
    private final List<NamespaceType> namespaces;
    private final List<String> writableAttributes;
    private final FlatMultivalueStrategyType flatMultivalueStrategy;
    private final ScimUUIDMappingCache cache;
    
    private WebTarget target; 
    private ObjectMapper mapper;

    public ScimDao(PluginConnectionType connection, ScimServiceSettings settings) throws LscServiceConfigurationException {
        LOGGER.debug("Init service");
        mapper = new ObjectMapper();
        this.entity = settings.getEntity();
        this.sourcePivot = getStringParameter(settings.getSourcePivot());
        this.pivot = getStringParameter(settings.getPivot());
        this.domain = getStringParameter(settings.getDomain());
        this.namespaces = settings.getSchema()!=null?settings.getSchema().getNamespace():new ArrayList<>();
        this.filter = getStringParameter(settings.getFilter()).map(this::replaceAllAliases);
        this.attributes = getStringParameter(settings.getAttributes()).map(this::replaceAllAliases);
        this.excludedAttributes = getStringParameter(settings.getExcludedAttributes()).map(this::replaceAllAliases);
        this.pageSize = Optional.ofNullable(settings.getPageSize()).filter(size -> size > 0);
        this.writableAttributes = Optional.ofNullable(settings.getWritableAttributes()).map(ValuesType::getString).orElse(null);
        this.flatMultivalueStrategy = Optional.ofNullable(settings.getFlatMultivalueStrategy()).orElse(FlatMultivalueStrategyType.ELEMENT_DIFF);

        cache = new ScimUUIDMappingCache(settings);

        Client client = AuthClientBuilder.build(connection);
        target = client.target(connection.getUrl());
    }

    private Optional<String> getStringParameter(String parameter) {
        return Optional.ofNullable(parameter).filter(currentfilter -> !currentfilter.trim().isEmpty());
    }

    public Map<String, LscDatasets> getList() throws LscServiceException {
        return getList(filter);
    }

    public String getSourcePivotName() {
        return sourcePivot.map(p -> p).orElse(getPivotName());
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
    
    public Map<String, Object> getDetailsByPivot(String pivotValue, String sourceUUIDValue) throws LscServiceException {
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
                    if (cache.isWriteEnabled()) cache.saveMapping(pivotValue, sourceUUIDValue, detail.get(ID).toString(), entity);
                    break;
                default:
                    throw new LscServiceException(String.format("Multiple results for %s %s", getEntityName(), pivotValue));
                }
            } else {
            	if (cache.isWriteEnabled()) {
            		cache.saveSourceUUID(pivotValue, sourceUUIDValue, entity);
            	}
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
            entityattributes.put(SCHEMAS, new ArrayList<String>());
            for (LscDatasetModification mod : lm.getLscAttributeModifications()) {
                String attrName = mod.getAttributeName();
                if (hasMultivalueSelector(attrName)) {
                    String baseName = extractBaseName(attrName);
                    ScimSelector selector = ScimSelector.parse(ScimSelector.extractBody(attrName));
                    String subField = extractSubField(attrName);
                    List<Object> existing = (List<Object>) entityattributes.get(baseName);
                    entityattributes.put(baseName, addToMultivalueAttribute(existing, selector, subField, mod.getValues()));
                } else {
                    entityattributes.put(attrName, getFirstValueAsString(mod.getValues()));
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
            	if (cache.isWriteEnabled()) {
                	Map<String, Object> results = mapper.readValue(response.readEntity(String.class), LinkedHashMap.class);
            		cache.updateScimId(lm.getMainIdentifier(), results.get(ID).toString(), entity);
            	}
				LOGGER.debug("SCIM response: \n{}", response);
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
    
    /**
     * Adds the given {@code values} into the multivalued list under the given {@code selector}
     * and {@code subField}.
     *
     * <p>Behavior:
     * <ul>
     *   <li>Empty selector ({@code attr[]}): primitive append (legacy).</li>
     *   <li>Selector + subField equal to {@code "value"}: multi-value yields multi-element
     *       (legacy multi-element behavior). If a previously-built element with matching
     *       selector exists without a {@code value} field (because a sub-field diff was
     *       processed first), the first value fills that slot before new elements are added.</li>
     *   <li>Selector + a non-{@code value} subField (e.g. {@code .streetAddress}): merges
     *       into the single element matching the selector — creating it if absent — so
     *       multiple datasets like {@code addresses[type eq "work"].streetAddress} and
     *       {@code addresses[type eq "work"].locality} produce ONE address with both fields.</li>
     * </ul>
     */
    private List<Object> addToMultivalueAttribute(List<Object> entityattribute, ScimSelector selector, String subField, List<Object> values) throws JsonProcessingException {
        List<Object> multivalues = Optional.ofNullable(entityattribute).orElse(new ArrayList<>());
        if (selector.isEmpty()) {
            for (Object modValue : values) {
                multivalues.add(isJson(modValue) ? mapper.readValue(modValue.toString(), Object.class) : modValue);
            }
            return multivalues;
        }
        boolean isValueField = ScimSelector.VALUE.equals(subField);
        if (isValueField) {
            Map<String, Object> existing = findElementMatchingSelector(multivalues, selector);
            for (Object modValue : values) {
                if (existing != null && !existing.containsKey(ScimSelector.VALUE)) {
                    existing.put(ScimSelector.VALUE, String.valueOf(modValue));
                    existing = null;
                } else {
                    Map<String, Object> element = new LinkedHashMap<>(selector.toElementMap());
                    element.put(ScimSelector.VALUE, String.valueOf(modValue));
                    multivalues.add(element);
                }
            }
        } else {
            Map<String, Object> existing = findElementMatchingSelector(multivalues, selector);
            if (existing == null) {
                existing = new LinkedHashMap<>(selector.toElementMap());
                multivalues.add(existing);
            }
            existing.put(subField, getFirstValueAsString(values));
        }
        return multivalues;
    }

    /** Returns the sub-field name after the {@code ]} of a selector path, or {@link ScimSelector#VALUE} when absent. */
    private static String extractSubField(String attrName) {
        String tail = StringUtils.substringAfter(attrName, "]");
        if (StringUtils.isEmpty(tail)) {
            return ScimSelector.VALUE;
        }
        if (tail.startsWith(".") && tail.length() > 1) {
            return tail.substring(1);
        }
        return ScimSelector.VALUE;
    }

    /** First element in the list whose entries include all selector clauses with matching values. */
    private static Map<String, Object> findElementMatchingSelector(List<Object> multivalues, ScimSelector selector) {
        for (Object o : multivalues) {
            if (o instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) o;
                boolean matches = true;
                for (Map.Entry<String, Object> e : selector.asMap().entrySet()) {
                    if (!java.util.Objects.equals(m.get(e.getKey()), e.getValue())) {
                        matches = false;
                        break;
                    }
                }
                if (matches) return m;
            }
        }
        return null;
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
                    for (ScimPathOperation op : buildPatchOperations(operation, diff, lm)) {
                        patchOp.addOperations(op);
                    }
                }
            }
            if (!patchOp.getOperations().isEmpty()) {
                String patchOpJson = mapper.writeValueAsString(patchOp);
                LOGGER.debug("SCIM payload: {}", patchOpJson);
                response = currentTarget.request(MediaType.APPLICATION_JSON_TYPE).method(HttpMethod.PATCH, Entity.entity(patchOpJson, MediaType.APPLICATION_JSON));
                if (!checkResponse(response)) {
                	response.bufferEntity();
                	String body = response.hasEntity() ? response.readEntity(String.class) : "<empty>";
                    LOGGER.error("Error {} ({}) while updating {} {}\r\n{}",  response.getStatus(), response.getStatusInfo(), getEntityName(), lm.getMainIdentifier(), body);
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

    /**
     * Builds the SCIM PATCH operation(s) corresponding to a single LSC diff. The emitted shape
     * depends on whether the attribute path carries a value selector and, for flat multivalue
     * paths, on the per-task {@code flatMultivalueStrategy}.
     *
     * <p><b>Flat multivalue paths</b> ({@code attr[]} — no type discriminator, e.g. {@code members[]},
     * {@code phoneNumbers[]}, extension scalars such as {@code urn:...:mobileNumbers}):
     * <pre>
     *   strategy            diff op  →  emitted ops
     *   ELEMENT_DIFF        REPLACE  →  per-value Remove[value eq] + one aggregated Add (buildFlatMultivalueDiffOps)
     *   (default)           ADD/DEL  →  single Add / Remove on the bare path
     *   WHOLESALE_REPLACE   any      →  single Replace with the full final list
     *                                   (REPLACE = values, ADD = dst ∪ new, DELETE = dst \ removed)
     * </pre>
     * ELEMENT_DIFF is required by Keycloak, which rejects a wholesale Replace on {@code group.members}
     * with HTTP 501. WHOLESALE_REPLACE is required by WSO2 Asgardeo and WSO2 IS on flat <i>extension</i>
     * multivalue paths (URN): they reject both {@code add} and unfiltered {@code remove} there and
     * accept only {@code replace}. The setting is per task — do not mix Keycloak {@code group.members}
     * (needs ELEMENT_DIFF) with extension flat attributes (need WHOLESALE_REPLACE) in the same task.
     *
     * <p><b>Compound selector paths</b> ({@code attr[selector]}, e.g. {@code emails[type eq "work"]}):
     * <pre>
     *   diff op   dst matches  new values  →  emitted ops
     *   REPLACE   1            1           →  Replace canonical.subField
     *   REPLACE   1            N           →  Remove canonical + Add baseName N elements
     *   REPLACE   &gt; 1        *           →  element-level diff: precise Remove[selector and subField eq "X"]
     *                                          per surplus + one targeted Add for net-new (buildElementLevelDiffOps)
     *   REPLACE   0            *           →  Add baseName N elements
     *   ADD       *            *           →  Add baseName N elements (never Replace)
     *   DELETE    *            N&gt;0       →  N Remove ops, each selector AND-ed with subField eq value
     *   DELETE    *            empty       →  Remove canonical (drops every match)
     * </pre>
     * A SCIM Replace on a valuePath rewrites <i>every</i> matching element (RFC 7644 §3.5.2.3), so the
     * fast-path Replace is used only with exactly one match; multiple matches fall back to the
     * element-level diff.
     *
     * <p><b>Known Keycloak limitation (deliberately not worked around):</b> when a single PATCH holds a
     * {@code remove} positioned before an {@code add} on the same attribute, Keycloak silently drops the
     * remove — even with a precise {@code subField eq "X"} filter (verified 2026-05-23). The element-level
     * diff can thus leave a transient duplicate on Keycloak, reconciled on the next sync run. We do not
     * reorder ops or split the PATCH to avoid it.
     */
    private List<ScimPathOperation> buildPatchOperations(String operation, LscDatasetModification diff, LscModifications lm) {
        String attrName = diff.getAttributeName();
        List<Object> values = diff.getValues();
        boolean isRemove = diff.getOperation().equals(DELETE_VALUES);
        boolean isReplace = diff.getOperation().equals(LscDatasetModification.LscDatasetModificationType.REPLACE_VALUES);
        if (hasMultivalueSelector(attrName) && isRemove) {
            String body = ScimSelector.extractBody(attrName);
            String baseName = extractBaseName(attrName);
            if (StringUtils.isBlank(body) && flatMultivalueStrategy == FlatMultivalueStrategyType.WHOLESALE_REPLACE) {
                List<Object> remaining = subtractFlatDstValues(lm.getDestinationBean(), baseName, values);
                String path = replaceAlias(baseName);
                Serializable value = stringValuesToJsonValues(remaining);
                LOGGER.debug("op: replace, path: {}, value: {}", path, value);
                return List.of(new ScimPathOperation(OperationType.REPLACE.getName(), path, value));
            }
            if (StringUtils.isNotBlank(body) && values != null && !values.isEmpty()) {
                String selectorFilter = ScimSelector.parse(body).toScimFilter();
                String subField = extractSubField(attrName);
                List<ScimPathOperation> ops = new ArrayList<>(values.size());
                for (Object v : values) {
                    String filter = selectorFilter + ScimSelector.AND_OPERATOR + subField + ScimSelector.EQ_OPERATOR + "\"" + escapeScimFilterString(v) + "\"";
                    String removePath = replaceAlias(baseName + "[" + filter + "]");
                    LOGGER.debug("op: remove, path: {}", removePath);
                    ops.add(new ScimPathOperation(OperationType.REMOVE.getName(), removePath, null));
                }
                return ops;
            }
        }
        if (hasMultivalueSelector(attrName) && !isRemove) {
            String baseName = extractBaseName(attrName);
            String body = ScimSelector.extractBody(attrName);
            if (StringUtils.isBlank(body)) {
                if (flatMultivalueStrategy == FlatMultivalueStrategyType.WHOLESALE_REPLACE) {
                    List<Object> finalValues = isReplace ? values : mergeFlatDstValues(lm.getDestinationBean(), baseName, values);
                    String path = replaceAlias(baseName);
                    Serializable value = stringValuesToJsonValues(finalValues);
                    LOGGER.debug("op: replace, path: {}, value: {}", path, value);
                    return List.of(new ScimPathOperation(OperationType.REPLACE.getName(), path, value));
                }
                if (isReplace) {
                    return buildFlatMultivalueDiffOps(lm.getDestinationBean(), baseName, values);
                }
                String path = replaceAlias(baseName);
                Serializable value = stringValuesToJsonValues(values);
                LOGGER.debug("op: {}, path: {}, value: {}", diff.getOperation(), path, value);
                return List.of(new ScimPathOperation(operation, path, value));
            }
            ScimSelector selector = ScimSelector.parse(body);
            String canonicalKey = baseName + "[" + selector.toScimFilter() + "]";
            String subField = extractSubField(attrName);
            int dstMatchCount = countMatchingElements(lm.getDestinationBean(), canonicalKey);
            boolean dstHasMatch = dstMatchCount > 0;
            if (isReplace && values.size() == 1 && dstMatchCount == 1) {
                String path = replaceAlias(canonicalKey + "." + subField);
                Serializable value = String.valueOf(values.get(0));
                LOGGER.debug("op: replace, path: {}, value: {}", path, value);
                return List.of(new ScimPathOperation(OperationType.REPLACE.getName(), path, value));
            }
            if (isReplace && dstMatchCount > 1) {
                List<ScimPathOperation> ops = buildElementLevelDiffOps(lm.getDestinationBean(), canonicalKey, baseName, selector, subField, values);
                if (!ops.isEmpty()) {
                    return ops;
                }
            }
            ArrayList<Map<String, Object>> elements = new ArrayList<>();
            for (Object v : values) {
                Map<String, Object> element = selector.toElementMap();
                element.put(subField, String.valueOf(v));
                elements.add(element);
            }
            String addPath = replaceAlias(baseName);
            ScimPathOperation addOp = new ScimPathOperation(OperationType.ADD.getName(), addPath, elements);
            if (isReplace && dstHasMatch) {
                ScimPathOperation removeOp = new ScimPathOperation(OperationType.REMOVE.getName(), replaceAlias(canonicalKey), null);
                LOGGER.debug("op: remove+add, removePath: {}, addPath: {}, elements: {}", removeOp.getPath(), addPath, elements);
                return List.of(removeOp, addOp);
            }
            LOGGER.debug("op: add, path: {}, elements: {}", addPath, elements);
            return List.of(addOp);
        }
        String path = replaceAlias(canonicalizePath(attrName));
        Serializable value = isRemove ? null : getFirstValueAsString(values);
        LOGGER.debug("op: {}, path: {}, value: {}", diff.getOperation(), path, value);
        return List.of(new ScimPathOperation(operation, path, value));
    }

    /**
     * Computes the element-level delta between the dst values exposed under {@code canonicalKey}
     * and the requested {@code values}, then materializes it as: one precise REMOVE op per
     * surplus dst value (filter {@code baseName[selector and subField eq "X"]}) plus a single
     * ADD op carrying every net-new value. Survivors already present in dst are left untouched.
     */
    private List<ScimPathOperation> buildElementLevelDiffOps(IBean dstBean, String canonicalKey,
            String baseName, ScimSelector selector, String subField, List<Object> values) {
        Set<Object> dstValues = dstBean != null
                ? Optional.ofNullable(dstBean.getDatasetById(canonicalKey)).orElse(Set.of())
                : Set.of();
        List<String> dstStrValues = new ArrayList<>(dstValues.size());
        for (Object v : dstValues) {
            dstStrValues.add(String.valueOf(v));
        }
        List<String> newStrValues = new ArrayList<>(values.size());
        for (Object v : values) {
            newStrValues.add(String.valueOf(v));
        }
        List<ScimPathOperation> ops = new ArrayList<>();
        String selectorFilter = selector.toScimFilter();
        for (String v : dstStrValues) {
            if (!newStrValues.contains(v)) {
                String filter = selectorFilter + ScimSelector.AND_OPERATOR + subField + ScimSelector.EQ_OPERATOR + "\"" + escapeScimFilterString(v) + "\"";
                String removePath = replaceAlias(baseName + "[" + filter + "]");
                LOGGER.debug("op: remove, path: {}", removePath);
                ops.add(new ScimPathOperation(OperationType.REMOVE.getName(), removePath, null));
            }
        }
        ArrayList<Map<String, Object>> toAdd = new ArrayList<>();
        for (String v : newStrValues) {
            if (!dstStrValues.contains(v)) {
                Map<String, Object> element = selector.toElementMap();
                element.put(subField, v);
                toAdd.add(element);
            }
        }
        if (!toAdd.isEmpty()) {
            String addPath = replaceAlias(baseName);
            LOGGER.debug("op: add, path: {}, elements: {}", addPath, toAdd);
            ops.add(new ScimPathOperation(OperationType.ADD.getName(), addPath, toAdd));
        }
        return ops;
    }

    /**
     * Element-level delta for flat multivalue paths ({@code attr[]}, no type discriminator).
     * Emits one REMOVE op per dst value missing from the requested set (filter
     * {@code baseName[value eq "X"]}) plus a single ADD carrying every net-new value.
     * Used in place of a wholesale REPLACE on the path, which is rejected by some SCIM
     * stacks (Keycloak returns 501 {@code patch-REPLACE-operation not supported for group.members}).
     *
     * <p>Dst scalars are collected from both the flat {@code baseName[]} bucket and any
     * selector-keyed bucket ({@code baseName[type eq "work"]}, etc.) — see
     * {@link #collectFlatDstValues}. This keeps element-level diff usable even when the
     * server adds a type discriminator to entries that were mapped flat on the source side.
     *
     * <p>Survivors already present in dst are left untouched, so audit logs and downstream
     * listeners only see the actual delta.
     */
    private List<ScimPathOperation> buildFlatMultivalueDiffOps(IBean dstBean, String baseName, List<Object> values) {
        Set<Object> dstValues = collectFlatDstValues(dstBean, baseName);
        List<String> dstStr = new ArrayList<>(dstValues.size());
        for (Object v : dstValues) {
            dstStr.add(toComparableValue(v));
        }
        List<String> newStr = new ArrayList<>(values.size());
        for (Object v : values) {
            newStr.add(toComparableValue(v));
        }
        List<ScimPathOperation> ops = new ArrayList<>();
        for (String v : dstStr) {
            if (!newStr.contains(v)) {
                String filter = ScimSelector.VALUE + ScimSelector.EQ_OPERATOR + "\"" + escapeScimFilterString(v) + "\"";
                String removePath = replaceAlias(baseName + "[" + filter + "]");
                LOGGER.debug("op: remove, path: {}", removePath);
                ops.add(new ScimPathOperation(OperationType.REMOVE.getName(), removePath, null));
            }
        }
        List<Object> toAdd = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (!dstStr.contains(newStr.get(i))) {
                toAdd.add(values.get(i));
            }
        }
        if (!toAdd.isEmpty()) {
            String addPath = replaceAlias(baseName);
            Serializable addValue = stringValuesToJsonValues(toAdd);
            LOGGER.debug("op: add, path: {}, value: {}", addPath, addValue);
            ops.add(new ScimPathOperation(OperationType.ADD.getName(), addPath, addValue));
        }
        return ops;
    }

    /**
     * Collects the dst scalar values for a flat multivalue attribute, walking both the
     * canonical {@code baseName[]} bucket and any selector-keyed bare key
     * ({@code baseName[type eq "work"]}) — the latter carries the SCIM element's
     * {@code value} field after {@link #processFlatDiffs}. Sub-field keys
     * ({@code baseName[selector].subAttr}) are skipped: they hold unrelated sub-fields,
     * not the comparable scalar.
     */
    private Set<Object> collectFlatDstValues(IBean dstBean, String baseName) {
        if (dstBean == null) {
            return Set.of();
        }
        Set<Object> collected = new LinkedHashSet<>();
        Set<Object> bare = dstBean.getDatasetById(baseName + "[]");
        if (bare != null) {
            collected.addAll(bare);
        }
        String prefix = baseName + "[";
        for (String key : dstBean.getAttributesNames()) {
            if (!key.startsWith(prefix) || key.equals(baseName + "[]")) {
                continue;
            }
            if (key.indexOf(']') != key.length() - 1) {
                continue;
            }
            Set<Object> vals = dstBean.getDatasetById(key);
            if (vals != null) {
                collected.addAll(vals);
            }
        }
        return collected;
    }

    /**
     * Merges the dst current values for a flat multivalue attribute with the requested
     * new values, deduplicating by comparable scalar form. Used by the
     * {@link FlatMultivalueStrategyType#WHOLESALE_REPLACE} strategy on {@code ADD_VALUES}:
     * the resulting list is sent as a single SCIM PATCH {@code replace} op so that
     * servers like Asgardeo (which reject {@code add} on extension flat paths) still
     * receive a valid request.
     */
    private List<Object> mergeFlatDstValues(IBean dstBean, String baseName, List<Object> newValues) {
        Set<Object> dstValues = collectFlatDstValues(dstBean, baseName);
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        for (Object v : dstValues) {
            merged.putIfAbsent(toComparableValue(v), v);
        }
        if (newValues != null) {
            for (Object v : newValues) {
                merged.putIfAbsent(toComparableValue(v), v);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * Subtracts the values to remove from the dst current values for a flat multivalue
     * attribute, matching by comparable scalar form. Used by the
     * {@link FlatMultivalueStrategyType#WHOLESALE_REPLACE} strategy on {@code DELETE_VALUES}
     * to emit a single {@code replace} op carrying the surviving values — Asgardeo rejects
     * wholesale {@code remove} without a selector filter.
     */
    private List<Object> subtractFlatDstValues(IBean dstBean, String baseName, List<Object> valuesToRemove) {
        Set<Object> dstValues = collectFlatDstValues(dstBean, baseName);
        if (dstValues.isEmpty()) {
            return List.of();
        }
        Set<String> drop = new LinkedHashSet<>();
        if (valuesToRemove != null) {
            for (Object v : valuesToRemove) {
                drop.add(toComparableValue(v));
            }
        }
        List<Object> remaining = new ArrayList<>(dstValues.size());
        for (Object v : dstValues) {
            if (!drop.contains(toComparableValue(v))) {
                remaining.add(v);
            }
        }
        return remaining;
    }

    /**
     * Normalizes a multivalue element to its comparable scalar form: if the value is a
     * JSON object carrying a {@code value} field (e.g. {@code {"value":"uuid-1"}}),
     * returns the {@code value} field's text; otherwise returns the raw string. Lets
     * dst (already-extracted scalars from {@link #processFlatDiffs}) and src (often
     * still wrapped {@code {value:...}} from LSC templating) be compared on equal terms.
     */
    private String toComparableValue(Object raw) {
        String s = String.valueOf(raw);
        if (s.length() > 1 && (s.charAt(0) == '{' || s.charAt(0) == '[')) {
            try {
                JsonNode node = mapper.readTree(s);
                if (node != null && node.isObject() && node.has(ScimSelector.VALUE)) {
                    return node.get(ScimSelector.VALUE).asText();
                }
            } catch (Exception ignore) {
                // not JSON — fall through and treat as raw scalar
            }
        }
        return s;
    }

    /** Escapes a string value for embedding inside a SCIM filter literal (RFC 7644 §3.4.2.2). */
    private static String escapeScimFilterString(Object v) {
        return String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Re-emits the selector inside [...] in canonical form (quoted strings, ordered clauses). */
    private String canonicalizePath(String attributeName) {
        if (!hasMultivalueSelector(attributeName)) {
            return attributeName;
        }
        String base = extractBaseName(attributeName);
        String body = ScimSelector.extractBody(attributeName);
        String tail = StringUtils.substringAfter(attributeName, "]");
        if (StringUtils.isBlank(body)) {
            return base + "[]" + tail;
        }
        return base + "[" + ScimSelector.parse(body).toScimFilter() + "]" + tail;
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
        pivotFilter.append(getPivotName()).append(EQ_OPERATOR).append(StringUtils.wrap(pivotValue.replace("'", "''"), '"'));
        return filter.map(f -> f + " and " + pivotFilter.toString()).orElse(pivotFilter.toString());
    }
    
    private static String getFirstValueAsString(List<Object> valuesList) {
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
     * Returns true if the flat attribute name carries a multivalue selector ({@code [...]}),
     * either on the attribute itself ({@code emails[type eq work]}) or on an ancestor
     * ({@code addresses[type eq work].streetAddress}). Does not imply that the leaf attribute
     * is itself multivalued.
     */
    private boolean hasMultivalueSelector(String attributeName) {
        return StringUtils.contains(attributeName, "[");
    }

    /**
     * Returns the base attribute name preceding the first {@code [} (e.g. {@code addresses}
     * for {@code addresses[type eq work].streetAddress}).
     */
    private String extractBaseName(String attributeName) {
        return StringUtils.substringBefore(attributeName, "[");
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
        JsonFlattener flattener = new JsonFlattener(jsonAttrsWithSchemaAlias).withFlattenMode(FlattenMode.KEEP_PRIMITIVE_ARRAYS);
        Map<String, Object> flattenDiffs = flattener.flattenAsMap();
        return processFlatDiffs(flattenDiffs);
    }
    
    /**
     * Normalizes a flattened SCIM JSON map into a single entry per multivalued element,
     * keyed by a canonical SCIM compound selector ({@code emails[type eq "home" and primary eq true]}).
     *
     * <p>Object-array entries ({@code emails[0].type}, {@code emails[0].value}, ...) are grouped
     * by their numeric index and rewritten using {@link ScimSelector#fromFlatElement(Map)}.
     * Object elements with no selector sub-attributes (only {@code value}) collapse into a
     * primitive list under {@code attr[]}.
     *
     * <p>Each compound selector entry is additionally emitted under the user-supplied form
     * declared in {@code writableAttributes} (when its canonicalization matches), so that
     * non-canonical lookups by LSC's BeanComparator hit the same value.
     */
    private Map<String, Object> processFlatDiffs(Map<String, Object> flattenDiffs) {
        Map<String, Object> normalized = normalizeArrayKeys(flattenDiffs);
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        List<String> consumed = new ArrayList<>();
        for (Entry<String, Object> entry : normalized.entrySet()) {
            Matcher m = OBJECT_ARRAY_KEY.matcher(entry.getKey());
            if (m.matches()) {
                String groupKey = m.group(1) + "[" + m.group(2) + "]";
                groups.computeIfAbsent(groupKey, k -> new LinkedHashMap<>()).put(m.group(3), entry.getValue());
                consumed.add(entry.getKey());
            }
        }
        consumed.forEach(normalized::remove);
        for (Entry<String, Map<String, Object>> group : groups.entrySet()) {
            String baseName = StringUtils.substringBefore(group.getKey(), "[");
            Map<String, Object> sub = group.getValue();
            Object value = sub.get(ScimSelector.VALUE);
            ScimSelector selector = ScimSelector.fromFlatElement(sub);
            if (selector.isEmpty()) {
                String key = baseName + "[]";
                List<Object> list = (List<Object>) normalized.computeIfAbsent(key, k -> new ArrayList<>());
                if (value != null) {
                    list.add(value);
                }
            } else {
                String canonicalKey = baseName + "[" + selector.toScimFilter() + "]";
                // Backward-compat: expose the value field directly under the selector key
                // (so existing datasets like emails[type eq "work"] continue to resolve).
                if (value != null) {
                    accumulate(normalized, canonicalKey, value);
                    String userForm = findWritableUserForm(canonicalKey);
                    if (userForm != null && !userForm.equals(canonicalKey)) {
                        accumulate(normalized, userForm, value);
                    }
                }
                // Per-sub-attribute keys (canonicalKey.subField) for every field that is
                // not part of the selector clauses — supports addresses[type eq "work"].streetAddress
                // and similar structured sub-fields.
                for (Map.Entry<String, Object> e : sub.entrySet()) {
                    String subAttr = e.getKey();
                    if (selector.has(subAttr)) {
                        continue;
                    }
                    String subKey = canonicalKey + "." + subAttr;
                    accumulate(normalized, subKey, e.getValue());
                    String userSubForm = findWritableUserForm(subKey);
                    if (userSubForm != null && !userSubForm.equals(subKey)) {
                        accumulate(normalized, userSubForm, e.getValue());
                    }
                }
            }
        }
        return normalized;
    }

    /**
     * Accumulates a value under {@code key}: if absent stores the value directly; if a single
     * value is already there promotes to a list and appends; if a list is already there
     * appends to it. Null values are skipped.
     */
    private static void accumulate(Map<String, Object> map, String key, Object value) {
        if (value == null) {
            map.putIfAbsent(key, null);
            return;
        }
        Object existing = map.get(key);
        if (existing == null) {
            map.put(key, value);
        } else if (existing instanceof List) {
            ((List<Object>) existing).add(value);
        } else {
            List<Object> list = new ArrayList<>();
            list.add(existing);
            list.add(value);
            map.put(key, list);
        }
    }

    /**
     * Returns the writableAttributes entry whose canonicalization equals {@code canonicalKey},
     * or {@code null} if none. Used to alias the dst bean under the user-written form so
     * non-canonical writableAttributes still resolve at lookup time.
     */
    private String findWritableUserForm(String canonicalKey) {
        if (writableAttributes == null) {
            return null;
        }
        for (String w : writableAttributes) {
            if (canonicalizePath(w).equals(canonicalKey)) {
                return w;
            }
        }
        return null;
    }

    /**
     * Normalizes the keys of the provided map based on the configured writable attributes
     * <p>
     * If an attribute is defined as an array type in {@code writableAttributes} (i.e. its name
     * appears with the "[]" suffix), the corresponding map entry key is converted to its array
     * form. Otherwise, the original key is preserved.
     * </p>
     */
    private Map<String, Object> normalizeArrayKeys(Map<String, Object> flattenDiffs) {
    	Map<String, Object> updatedMap = new LinkedHashMap<>();
    	for (Map.Entry<String, Object> entry: flattenDiffs.entrySet()) {
    		String arrayKey = entry.getKey()+"[]";
    		boolean isArray = entry.getValue() instanceof ArrayList
    				|| (writableAttributes != null && writableAttributes.contains(arrayKey));
    		updatedMap.put(isArray?arrayKey:entry.getKey(), entry.getValue());
		}
    	return updatedMap;
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
    
    /**
     * Lower-bound count of dst elements matching the given selector, derived from the
     * widest dataset cardinality found under {@code canonicalKey} (bare key for elements
     * with a {@code value} field, or any {@code canonicalKey.subField} entry). Used to
     * decide whether a SCIM Replace on a valuePath is safe (single match) or would
     * affect multiple records (must fall back to Remove+Add).
     */
    private int countMatchingElements(IBean bean, String canonicalKey) {
        if (bean == null) {
            return 0;
        }
        Set<Object> direct = bean.getDatasetById(canonicalKey);
        int max = direct != null ? direct.size() : 0;
        String prefix = canonicalKey + ".";
        for (String key : bean.getAttributesNames()) {
            if (key.startsWith(prefix)) {
                Set<Object> sub = bean.getDatasetById(key);
                if (sub != null && sub.size() > max) {
                    max = sub.size();
                }
            }
        }
        return max;
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

    private boolean isJson(Object raw) {
        try {
			if (raw instanceof String stringObj) {
        		return mapper.readTree(stringObj) != null;
    		} else {
    			return false;
    		}
    	} catch (Exception e) { 
    		return false; 
    	}
    }

}
