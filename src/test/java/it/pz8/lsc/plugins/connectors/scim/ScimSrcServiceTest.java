package it.pz8.lsc.plugins.connectors.scim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lsc.LscDatasets;
import org.lsc.beans.IBean;
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;

/**
 * @author Giuseppe Amato
 *
 */
class ScimSrcServiceTest {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ScimSrcServiceTest.class);
    
    private static final int EXPOSED_PORT = 9443;
    private static final String IMAGE_NAME = "wso2/wso2is:5.10.0-alpine3.11";
    private static final int TIMEOUT = 300;
    private static final String BASEPATH = "https://localhost:%d/scim2";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final boolean FROM_SAME_SERVICE = true;
    
    private static int mappedPort;
    private static GenericContainer<?> wso2ids;

    private static TaskType task;
    private static ServiceType.Connection connection;
    private static ScimServiceSettings serviceSettings;
    private static PluginSourceServiceType pluginSourceService;
    private static PluginDestinationServiceType pluginDestinationService;

    @BeforeAll
    static void setup() throws Exception {
        wso2ids = new GenericContainer<>(IMAGE_NAME);
        wso2ids.withExposedPorts(EXPOSED_PORT);
        wso2ids.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(TIMEOUT)));        
        wso2ids.start();
    
        mappedPort = wso2ids.getMappedPort(EXPOSED_PORT);
        LOGGER.info(String.format("Mapped port: %d:%d", mappedPort, EXPOSED_PORT));
        
        pluginSourceService = mock(PluginSourceServiceType.class);
        pluginDestinationService = mock(PluginDestinationServiceType.class);
        serviceSettings = mock(ScimServiceSettings.class);
        task = mock(TaskType.class);
        PluginConnectionType connectionType = mock(PluginConnectionType.class);
        connection = mock(ServiceType.Connection.class);

        when(connectionType.getUrl()).thenReturn(String.format(BASEPATH, mappedPort));
        when(connectionType.getUsername()).thenReturn(USERNAME);
        when(connectionType.getPassword()).thenReturn(PASSWORD);
        when(connection.getReference()).thenReturn(connectionType);  
        when(pluginSourceService.getConnection()).thenReturn(connection);
        when(pluginSourceService.getAny()).thenReturn(ImmutableList.of(serviceSettings));
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
    void constructorWithoutSettingsShouldFail() throws LscServiceException {
        when(pluginSourceService.getAny()).thenReturn(null);
        ScimSrcService testSrcService;
        try {
            testSrcService = new ScimSrcService(task);
        } catch (LscServiceConfigurationException e) {
            testSrcService = null;
        }
        assertThat(testSrcService).isNull();
        when(pluginSourceService.getAny()).thenReturn(ImmutableList.of(serviceSettings));
    }
    
    @Test
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
    void constructorWithoutConnectionSettingsShouldFail() throws LscServiceException {
        when(pluginSourceService.getConnection().getReference()).thenReturn(null);
        ScimSrcService testSrcService;
        try {
            testSrcService = new ScimSrcService(task);
        } catch (LscServiceConfigurationException e) {
            testSrcService = null;
        }
        assertThat(testSrcService).isNull();
        when(pluginSourceService.getConnection()).thenReturn(connection);
    }
    
    @Test
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
    void getBeanShouldReturnNullWhenEmptyDataset() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn(null);
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        assertThat(testSrcService.getBean("id", new LscDatasets(), FROM_SAME_SERVICE)).isNull();
    }

    @Test
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
    void getBeanShouldReturnMainIdentifierSetToIdWhenMailAsPivot() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn("emails");
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        String firstUserPivotValue = pivots.keySet().stream().findFirst().get();
        IBean bean = testSrcService.getBean("emails", pivots.get(firstUserPivotValue), FROM_SAME_SERVICE);
        assertThat(bean.getMainIdentifier()).isEqualTo(pivots.get(firstUserPivotValue).getStringValueAttribute("id"));
    }
    
    @Test
    void getBeanShouldReturnIdAndMailWhenMailAsPivot() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn("emails");
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        String firstUserPivotValue = pivots.keySet().stream().findFirst().get();
        IBean bean = testSrcService.getBean("emails", pivots.get(firstUserPivotValue), FROM_SAME_SERVICE);
        assertThat(bean.getDatasetFirstValueById("id")).isEqualTo(pivots.get(firstUserPivotValue).getStringValueAttribute("id"));
        assertThat(bean.getDatasetFirstValueById("emails[]")).isEqualTo(pivots.get(firstUserPivotValue).getStringValueAttribute("emails"));
    }
    
    @Test
    void getBeanShouldReturnNullWhenNonExistingUserFromAnotherService() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("");
        when(serviceSettings.getPivot()).thenReturn("userName");
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        LscDatasets nonExistingIdDataset = new LscDatasets(ImmutableMap.of("userName", "pippo"));
        IBean bean = testSrcService.getBean("userName", nonExistingIdDataset, !FROM_SAME_SERVICE);
        assertThat(bean).isNull();
    }    
    
    @Test
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
    void listPivotShouldReturnEmptyWhenNoResultByFilter() throws Exception {
        when(serviceSettings.getFilter()).thenReturn("userName co pippo");
        when(serviceSettings.getPivot()).thenReturn(null);
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        ScimSrcService testSrcService = new ScimSrcService(task);
        Map<String, LscDatasets> pivots = testSrcService.getListPivots();
        assertThat(pivots).isEmpty();
    }

    @Test
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

}
