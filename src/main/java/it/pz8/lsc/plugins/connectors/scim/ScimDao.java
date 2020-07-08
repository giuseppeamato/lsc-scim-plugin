package it.pz8.lsc.plugins.connectors.scim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.lsc.LscDatasets;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.exception.LscServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.JsonFlattener;

import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;

/**
 * @author Giuseppe Amato
 *
 */
public class ScimDao {

    public static final String USERS = "Users";
    public static final String GROUPS = "Groups";
    public static final String RESOURCES = "Resources";
    public static final String ID = "id";

    private static final Logger LOGGER = LoggerFactory.getLogger(ScimDao.class);

    private final String entity;
    private final Optional<String> pivot;
    private final Optional<String> domain;
    private final Optional<Integer> pageSize;
    private final Optional<String> filter;
    private final Optional<String> attributes;
    private final Optional<String> excludedAttributes;

    private WebTarget target; 
    private ObjectMapper mapper;

    public ScimDao(PluginConnectionType connection, ScimServiceSettings settings) {
        LOGGER.debug("Init service");
        mapper = new ObjectMapper();
        this.entity = settings.getEntity();
        this.pivot = getStringParameter(settings.getPivot());
        this.domain = getStringParameter(settings.getDomain());
        this.filter = getStringParameter(settings.getFilter());
        this.attributes = getStringParameter(settings.getAttributes());
        this.excludedAttributes = getStringParameter(settings.getExcludedAttributes());
        this.pageSize = Optional.ofNullable(settings.getPageSize()).filter(size -> size > 0);
        Client client = ClientBuilder.newClient().register(new BasicAuthenticator(connection.getUsername() , connection.getPassword()));
        target = client.target(connection.getUrl());
    }

    private Optional<String> getStringParameter(String parameter) {
        return Optional.ofNullable(parameter).filter(filter -> !filter.trim().isEmpty());
    }

    public Map<String, LscDatasets> getList() throws LscServiceException {
        return getList(filter);
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

    private static boolean checkResponse(Response response) {
        return Response.Status.Family.familyOf(response.getStatus()) == Response.Status.Family.SUCCESSFUL;
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
            LOGGER.debug(String.format("Retrieve %s detail from: %s ", entity.equals(USERS)?"user":"group", currentTarget.getUri().toString()));
            response = currentTarget.request().accept(MediaType.APPLICATION_JSON).get(Response.class);
            if (!checkResponse(response)) {
                String errorMessage = String.format("status: %d, message: %s", response.getStatus(), response.readEntity(String.class));
                LOGGER.error(errorMessage);
                throw new ProcessingException(errorMessage);
            }
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new NotFoundException(String.format("%s %s cannot be found", entity.equals(USERS)?"user":"group", id));
            }
            return JsonFlattener.flattenAsMap(response.readEntity(String.class));
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    public Optional<Entry<String, LscDatasets>> findFirstByPivot(String pivotValue) throws LscServiceException {
        StringBuilder pivotFilter = new StringBuilder();
        pivotFilter.append(getPivotName()).append(" eq \"").append(pivotValue.replaceAll("'", "''")).append("\"");
        String computedFilter = filter.map(f -> f + " and " + pivotFilter.toString()).orElse(pivotFilter.toString());
        return getList(Optional.of(computedFilter)).entrySet().stream().findFirst();
    }
}
