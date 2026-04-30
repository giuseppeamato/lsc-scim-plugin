package it.pz8.lsc.plugins.connectors.scim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.lsc.LscDatasets;
import org.lsc.beans.IBean;
import org.lsc.configuration.ConnectionType;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.PluginDestinationServiceType;
import org.lsc.configuration.PluginSourceServiceType;
import org.lsc.configuration.ServiceType;
import org.lsc.configuration.TaskType;
import org.lsc.exception.LscServiceConfigurationException;
import org.lsc.exception.LscServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import it.pz8.lsc.plugins.connectors.scim.generated.Oauth2ConnectionSettings;
import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;
import it.pz8.lsc.plugins.connectors.scim.rs.AuthClientBuilder;
import it.pz8.lsc.plugins.connectors.scim.rs.TestSSLUtils;

/**
 * @author Giuseppe Amato
 *
 */
@TestMethodOrder(OrderAnnotation.class)
class ScimSrcServiceTest {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ScimSrcServiceTest.class);
    
    private static final int EXPOSED_PORT = 9443;
    private static final String IMAGE_NAME = "wso2/wso2is:7.2.0-alpine";
    private static final int TIMEOUT = 300;
    private static final String SCIM_BASEPATH = "https://localhost:%d/scim2";
    private static final String WSO2_MGMTPATH = "https://localhost:%d/api/server/v1";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final boolean FROM_SAME_SERVICE = true;
    
    private static final String OAUTH2_TOKENURL = "https://localhost:%d/oauth2/token";
    private static final String OAUTH2_SCOPE  ="internal_user_mgt_create internal_user_mgt_list internal_user_mgt_view  internal_user_mgt_update internal_user_mgt_delete";
    private static final String OAUTH2_CLIENTID = "q2orWbmKJwEeBRyMzpJmFjO8nmYc";
    private static final String OAUTH2_CLIENTSECRET = "RmcpR3fml352f58N__rtL9TuFYgDWlfVnV9zRXsRjtsa";
	
    private static int mappedPort;
    private static GenericContainer<?> wso2ids;

    private static TaskType task;
    private static ServiceType.Connection connection;
    private static PluginConnectionType connectionType; 
    private static ScimServiceSettings serviceSettings;
    private static PluginSourceServiceType pluginSourceService;
    private static PluginDestinationServiceType pluginDestinationService;

    @BeforeAll
    static void setup() throws LscServiceConfigurationException {
        wso2ids = new GenericContainer<>(IMAGE_NAME);
        wso2ids.withExposedPorts(EXPOSED_PORT);
        wso2ids.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(TIMEOUT)));        
        wso2ids.start();
    
        mappedPort = wso2ids.getMappedPort(EXPOSED_PORT);
        LOGGER.info("Mapped port: {}:{}", mappedPort, EXPOSED_PORT);
        
    	createIdsApplication();
    }
    
    private static void createIdsApplication() throws LscServiceConfigurationException {
    	LOGGER.info("Create Oauth2 M2M Application");
   		try {
			connectionType = mock(PluginConnectionType.class);
			when(connectionType.getUrl()).thenReturn(String.format(SCIM_BASEPATH, mappedPort));
			when(connectionType.getUsername()).thenReturn(USERNAME);
			when(connectionType.getPassword()).thenReturn(PASSWORD);
			Client client = AuthClientBuilder.build(connectionType);
			WebTarget basetarget = client.target(String.format(WSO2_MGMTPATH, mappedPort));
			String applicationId = createApplicationId(basetarget);
			String scimUsersApiResourceId = getApiResourceId(basetarget, "Users");
			authorizeAPI(basetarget, applicationId, scimUsersApiResourceId);
		} catch (IOException e) {
			throw new LscServiceConfigurationException(e);
		}
    }
    
    private static String createApplicationId(WebTarget basetarget) throws IOException {
		WebTarget currentTarget = basetarget.path("/applications");
		InputStream appPayloadStream = ScimSrcServiceTest.class.getResourceAsStream("/application.json");
        if (appPayloadStream == null) {
            throw new IOException("Application payload not found in classpath!");
        }
        String appPayload = new String(appPayloadStream.readAllBytes(), StandardCharsets.UTF_8);
        Response response = currentTarget.request(MediaType.APPLICATION_JSON_TYPE).post(Entity.json(appPayload));
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
        	throw new IOException(response.getStatusInfo().getReasonPhrase());
        }
    	return StringUtils.substringAfterLast(response.getLocation().toString(), "/");    			
    }
    
    private static String getApiResourceId(WebTarget basetarget, String entity) throws IOException {
		WebTarget currentTarget = basetarget.path("/api-resources").queryParam("filter", "identifier eq /scim2/"+entity);
        Response response = currentTarget.request().accept(MediaType.APPLICATION_JSON).get();
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
        	throw new IOException(response.getStatusInfo().getReasonPhrase());
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response.readEntity(String.class));
        return rootNode.path("apiResources").get(0).path("id").asText();
    }

    private static void authorizeAPI(WebTarget basetarget, String applicationId, String resourceId) throws IOException {
		WebTarget currentTarget = basetarget.path(String.format("/applications/%s/authorized-apis", applicationId));
        String appPayload = String.format("{\"id\": \"%s\",\"policyIdentifier\": \"RBAC\",\"scopes\": [\"internal_user_mgt_create\", \"internal_user_mgt_list\", \"internal_user_mgt_view\", \"internal_user_mgt_update\", \"internal_user_mgt_delete\"]}", resourceId);
        Response response = currentTarget.request(MediaType.APPLICATION_JSON_TYPE).post(Entity.json(appPayload));
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
        	throw new IOException(response.getStatusInfo().getReasonPhrase());
        }    			
    }
    
    @BeforeEach
    void testSetup() {
        pluginSourceService = mock(PluginSourceServiceType.class);
        pluginDestinationService = mock(PluginDestinationServiceType.class);
        serviceSettings = mock(ScimServiceSettings.class);
        task = mock(TaskType.class);
        connectionType = mock(PluginConnectionType.class);
        connection = mock(ServiceType.Connection.class);

        when(connectionType.getUrl()).thenReturn(String.format(SCIM_BASEPATH, mappedPort));
        when(connectionType.getUsername()).thenReturn(USERNAME);
        when(connectionType.getPassword()).thenReturn(PASSWORD);
        when(connection.getReference()).thenReturn(connectionType);  
        when(pluginSourceService.getConnection()).thenReturn(connection);
        when(pluginSourceService.getAny()).thenReturn(List.of(serviceSettings));
        when(serviceSettings.getEntity()).thenReturn("Users");
        when(task.getBean()).thenReturn("org.lsc.beans.SimpleBean");
        when(task.getPluginSourceService()).thenReturn(pluginSourceService);
        when(task.getPluginDestinationService()).thenReturn(pluginDestinationService);    	
    }
    
    @AfterAll
    static void close() {
        wso2ids.close();
    }
    
    @Test
    @Order(1)
    void constructorWithoutSettingsShouldFail() throws LscServiceException {
        when(pluginSourceService.getAny()).thenReturn(null);
        ScimSrcService testSrcService;
        try {
            testSrcService = new ScimSrcService(task);
        } catch (LscServiceConfigurationException e) {
            testSrcService = null;
        }
        assertThat(testSrcService).isNull();
        when(pluginSourceService.getAny()).thenReturn(List.of(serviceSettings));
    }
    
    @Test
    @Order(2)
    void constructorWithIncorrectSettingsShouldFail() throws LscServiceException {
        when(serviceSettings.getEntity()).thenReturn("Utenti");
        ScimSrcService testSrcService;
        try {
            testSrcService = new ScimSrcService(task);
        } catch (LscServiceConfigurationException e) {
            testSrcService = null;
        }
        assertThat(testSrcService).isNull();
        when(serviceSettings.getEntity()).thenReturn("Users");
    }
    
    @Test
    @Order(3)
    void constructorWithoutConnectionSettingsShouldFail() throws LscServiceException {
        when(pluginSourceService.getConnection().getReference()).thenReturn(null);
        ScimSrcService testSrcService;
        try {
            testSrcService = new ScimSrcService(task);
        } catch (LscServiceConfigurationException e) {
            testSrcService = null;
        }
        assertThat(testSrcService).isNull();
        when(pluginSourceService.getConnection().getReference()).thenReturn(connectionType);
    }
    
    @Test
    @Order(4)
    void listPivotShouldReturnEmptyWhenNoResult() throws LscServiceException {
        when(serviceSettings.getFilter()).thenReturn("id eq 'pippo'");
        when(serviceSettings.getPivot()).thenReturn(null);
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> listPivots = testSrcService.getListPivots();
        assertThat(listPivots).isEmpty();
    }
    
    @Test
    @Order(5)
    void listPivotShouldReturnOneUserWhenOneResult() throws LscServiceException {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn(null);
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> listPivots = testSrcService.getListPivots();
        String first = listPivots.keySet().stream().findFirst().get();
        when(serviceSettings.getFilter()).thenReturn("id eq '" + first + "'");
        testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> actual = testSrcService.getListPivots();
        assertThat(actual).hasSize(1);
    }
    
    @Test
    @Order(6)
    void getBeanShouldReturnNullWhenEmptyDataset() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn(null);
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        assertThat(testSrcService.getBean("id", new LscDatasets(), FROM_SAME_SERVICE)).isNull();
    }

    @Test
    @Order(7)
    void getBeanShouldReturnNullWhenNoMatchingId() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn(null);
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        LscDatasets nonExistingIdDataset = new LscDatasets(ImmutableMap.of("id", "pippo"));
        assertThat(testSrcService.getBean("id", nonExistingIdDataset, FROM_SAME_SERVICE)).isNull();
    }
    
    @Test
    @Order(8)
    void getBeanShouldReturnMainIdentifierSetToIdWhenDefaultPivot() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn(null);
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        String firstUserPivotValue = pivots.keySet().stream().findFirst().get();        
        IBean bean = testSrcService.getBean("id", pivots.get(firstUserPivotValue), FROM_SAME_SERVICE);
        assertThat(bean.getMainIdentifier()).isEqualTo(pivots.get(firstUserPivotValue).getStringValueAttribute("id"));
    }

    @Test
    @Order(9)
    void getBeanShouldReturnMainIdentifierSetToIdWhenUsernameAsPivot() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn("userName");
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        String firstUserPivotValue = pivots.keySet().stream().findFirst().get();
        IBean bean = testSrcService.getBean("userName", pivots.get(firstUserPivotValue), FROM_SAME_SERVICE);
        assertThat(bean.getMainIdentifier()).isEqualTo(pivots.get(firstUserPivotValue).getStringValueAttribute("id"));
    }
    
    @Test
    @Order(10)
    void getBeanShouldReturnIdAndUsernameWhenUsernameAsPivot() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn("userName");
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        String firstUserPivotValue = pivots.keySet().stream().findFirst().get();
        IBean bean = testSrcService.getBean("userName", pivots.get(firstUserPivotValue), FROM_SAME_SERVICE);
        assertThat(bean.getDatasetFirstValueById("id")).isEqualTo(pivots.get(firstUserPivotValue).getStringValueAttribute("id"));
        assertThat(bean.getDatasetFirstValueById("userName")).isEqualTo(pivots.get(firstUserPivotValue).getStringValueAttribute("userName"));
    }
    
    @Test
    @Order(11)
    void getBeanShouldReturnNullWhenNonExistingUserFromAnotherService() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn("userName");
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        LscDatasets nonExistingIdDataset = new LscDatasets(ImmutableMap.of("userName", "pluto"));
        IBean bean = testSrcService.getBean("userName", nonExistingIdDataset, !FROM_SAME_SERVICE);
        assertThat(bean).isNull();
    }    
    
    @Test
    @Order(12)
    void getBeanShouldReturnBeanWithIdWhenFromAnotherService() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn("userName");
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        String firstUserPivotValue = pivots.keySet().stream().findFirst().get();
        LscDatasets datasets = new LscDatasets(ImmutableMap.of("userName", firstUserPivotValue));
        IBean bean = testSrcService.getBean("userName", datasets, !FROM_SAME_SERVICE);        
        assertThat(bean.getDatasetFirstValueById("id")).isNotBlank();
    }
    
    @Test
    @Order(13)
    void listPivotShouldReturnEmptyWhenNoResultByFilter() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("userName co pluto");
        when(serviceSettings.getPivot()).thenReturn(null);
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        assertThat(pivots).isEmpty();
    }

    @Test
    @Order(14)
    void getBeanShouldNotReturnEmailsWhenAttributesDoesntContainEmailsField() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn(null);
        when(serviceSettings.getAttributes()).thenReturn("id,userName,name");
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        String firstUserPivotValue = pivots.keySet().stream().findFirst().get();
        IBean bean = testSrcService.getBean("id", pivots.get(firstUserPivotValue), FROM_SAME_SERVICE);
        assertThat(bean.getDatasetById("emails[]")).isNull();
        assertThat(bean.getDatasetFirstValueById("id")).isEqualTo(firstUserPivotValue);
    }

    @Test 
    @Order(15)
    void getBeanShouldNotReturnEmailsWhenExcludedAttributesContainsEmailsField() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn(null);
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn("emails");
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        String firstUserPivotValue = pivots.keySet().stream().findFirst().get();
        IBean bean = testSrcService.getBean("id", pivots.get(firstUserPivotValue), FROM_SAME_SERVICE);
        assertThat(bean.getDatasetFirstValueById("id")).isNotBlank();
        assertThat(bean.getDatasetFirstValueById("emails[]")).isBlank();
    }

    @Test
    @Order(16)
    void taskBeanWithoutPublicNoArgContructorShouldFail() throws LscServiceException {
    	when(task.getBean()).thenReturn("java.lang.Integer");
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn("userName");
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        String firstUserPivotValue = pivots.keySet().stream().findFirst().get();
        IBean bean = null;
        try {
            bean = testSrcService.getBean("id", pivots.get(firstUserPivotValue), FROM_SAME_SERVICE);
        } catch (LscServiceException e) {
        	bean = null;
        }
        assertThat(bean).isNull();
        try {
            bean = testSrcService.getBean("id", pivots.get(firstUserPivotValue), !FROM_SAME_SERVICE);
        } catch (LscServiceException e) {
        	bean = null;
        }
        assertThat(bean).isNull();
    }
    
    @Test 
    @Order(17)
    void returnSupportedConnectionType() throws Exception {
        ScimSrcService testSrcService = new ScimSrcService(task);
        Collection<Class<? extends ConnectionType>> supportedTypes = testSrcService.getSupportedConnectionType();
        assertThat(supportedTypes).contains(PluginConnectionType.class);
    }

    @Test
    @Order(18)
    void getBeanUnauthenticatedShouldFail() throws LscServiceException {
    	when(connectionType.getPassword()).thenReturn("");
    	IBean bean = null;
    	try {
    		ScimSrcService testSrcService = new ScimSrcService(task);
    		bean = testSrcService.getBean("id", new LscDatasets(), FROM_SAME_SERVICE);
    	} catch (LscServiceException e) {
    		bean = null;
    	}
    	assertThat(bean).isNull();
    }
    
    @Test
    @Order(19)
    void listPivotOauth2() throws LscServiceException {
    	Oauth2ConnectionSettings oauth2Settings = mock(Oauth2ConnectionSettings.class);
    	when(connectionType.getAny()).thenReturn(List.of(oauth2Settings));
    	when(oauth2Settings.getTokenURL()).thenReturn(String.format(OAUTH2_TOKENURL, mappedPort));
    	when(oauth2Settings.getScope()).thenReturn(OAUTH2_SCOPE);
		when(oauth2Settings.getClientId()).thenReturn(OAUTH2_CLIENTID);
		when(oauth2Settings.getClientSecret()).thenReturn(OAUTH2_CLIENTSECRET);
		Map<String, LscDatasets> listPivots = null;
    	try {
    		TestSSLUtils.disableSSLVerification();
    		ScimSrcService testSrcService = new ScimSrcService(task);
    		listPivots = testSrcService.getListPivots();
    	} catch (Exception e) {
    		e.printStackTrace();
    		listPivots = null;
    	}
    	assertThat(listPivots).isNotNull();
    }
    
    @Test
    @Order(20)
    void listPivotOauth2WithRefreshToken() throws LscServiceException {
    	Oauth2ConnectionSettings oauth2Settings = mock(Oauth2ConnectionSettings.class);
    	when(connectionType.getAny()).thenReturn(List.of(oauth2Settings));
    	when(oauth2Settings.getTokenURL()).thenReturn(String.format(OAUTH2_TOKENURL, mappedPort));
    	when(oauth2Settings.getScope()).thenReturn(OAUTH2_SCOPE);
		when(oauth2Settings.getClientId()).thenReturn(OAUTH2_CLIENTID);
		when(oauth2Settings.getClientSecret()).thenReturn(OAUTH2_CLIENTSECRET);
		Map<String, LscDatasets> listPivots = null;
    	try {
    		TestSSLUtils.disableSSLVerification();
    		ScimSrcService testSrcService = new ScimSrcService(task);
    		listPivots = testSrcService.getListPivots();    		
    		
    		long startTime = System.currentTimeMillis();
            long timeoutMillis = TimeUnit.SECONDS.toMillis(90);
            listPivots = null;
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
            	Thread.sleep(15000);
            	try {
            		listPivots = testSrcService.getListPivots();
            	} catch (Exception e) {
            		listPivots = null;
            	}
            }
            assertThat(listPivots).isNotNull();
    	} catch (Exception e) {
    		e.printStackTrace();
    		listPivots = null;
    	}
    	assertThat(listPivots).isNotNull();
    }
    
}
