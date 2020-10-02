package it.pz8.lsc.plugins.connectors.scim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lsc.LscDatasetModification.LscDatasetModificationType.ADD_VALUES;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.lsc.LscDatasetModification;
import org.lsc.LscDatasetModification.LscDatasetModificationType;
import org.lsc.LscDatasets;
import org.lsc.LscModificationType;
import org.lsc.LscModifications;
import org.lsc.beans.IBean;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.PluginDestinationServiceType;
import org.lsc.configuration.ServiceType;
import org.lsc.configuration.TaskType;
import org.lsc.exception.LscServiceConfigurationException;
import org.lsc.exception.LscServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.google.common.collect.ImmutableList;

import it.pz8.lsc.plugins.connectors.scim.generated.NamespaceType;
import it.pz8.lsc.plugins.connectors.scim.generated.SchemasType;
import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;

/**
 * @author Giuseppe Amato
 *
 */
@TestMethodOrder(OrderAnnotation.class)
class ScimDstServiceTest {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ScimDstServiceTest.class);

    private static final int EXPOSED_PORT = 9443;  
    private static final String IMAGE_NAME = "wso2/wso2is:5.10.0-alpine3.11";
    private static final int TIMEOUT = 300;
    private static final String BASEPATH = "https://localhost:%d/scim2";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";

    private static int mappedPort;
    private static GenericContainer<?> wso2ids;

    private static TaskType task;
    private static ScimServiceSettings serviceSettings;
    private static PluginConnectionType connectionType;
    private static PluginDestinationServiceType pluginDestinationService;
    private static ScimDstService testDstService;

    @BeforeAll
    static void setup() throws Exception {
        wso2ids = new GenericContainer<>(IMAGE_NAME);
        wso2ids.withExposedPorts(EXPOSED_PORT);      
        wso2ids.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(TIMEOUT)));
        wso2ids.withLogConsumer(new Slf4jLogConsumer(LOGGER));
        wso2ids.start();

        mappedPort = wso2ids.getMappedPort(EXPOSED_PORT);
        LOGGER.info(String.format("Mapped port: %d:%d", mappedPort, EXPOSED_PORT));
        
        pluginDestinationService = mock(PluginDestinationServiceType.class);
        serviceSettings = mock(ScimServiceSettings.class);
        task = mock(TaskType.class);
        connectionType = mock(PluginConnectionType.class);
        ServiceType.Connection connection = mock(ServiceType.Connection.class);

        when(connectionType.getUrl()).thenReturn(String.format(BASEPATH, mappedPort));
        when(connectionType.getUsername()).thenReturn(USERNAME);
        when(connectionType.getPassword()).thenReturn(PASSWORD);
        when(connection.getReference()).thenReturn(connectionType);
        when(pluginDestinationService.getConnection()).thenReturn(connection);
        when(pluginDestinationService.getAny()).thenReturn(ImmutableList.of(serviceSettings));
        when(serviceSettings.getEntity()).thenReturn("Users");
        when(serviceSettings.getSchema()).thenReturn(createScimSchema());
        when(serviceSettings.getFilter()).thenReturn(null);
        when(serviceSettings.getPivot()).thenReturn("userName");
        when(serviceSettings.getSourcePivot()).thenReturn("uid");
        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        when(task.getBean()).thenReturn("org.lsc.beans.SimpleBean");
        when(task.getPluginDestinationService()).thenReturn(pluginDestinationService);
    }
  
    static SchemasType createScimSchema() {
        List<NamespaceType> nsList = new ArrayList<>();
        NamespaceType ns = new NamespaceType();
        ns.setAlias("ENTERPRISE_USER");
        ns.setUri("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
        nsList.add(ns);
        SchemasType schema = new SchemasType();
        schema.getNamespace().addAll(nsList);
        return schema;
    }
    
    @AfterAll
    static void close() {
        wso2ids.close();
    }
    
    @Test
    @Order(1)
    void getListPivots() throws LscServiceException {
        testDstService = new ScimDstService(task);
        Map<String, LscDatasets> bean = testDstService.getListPivots();
        assertThat(bean).isNotNull();
        assertThat(bean.size()).isPositive();
    }
    
    @Test
    @Order(2)
    void addUser() throws LscServiceException {
        testDstService = new ScimDstService(task);
        LscModifications lm = new LscModifications(LscModificationType.CREATE_OBJECT);
        lm.setMainIdentifer("pippo");
        LscDatasetModification username = new LscDatasetModification(ADD_VALUES, "userName", ImmutableList.of("pippo"));
        LscDatasetModification password = new LscDatasetModification(ADD_VALUES, "password", ImmutableList.of("123456"));
        LscDatasetModification firstname = new LscDatasetModification(ADD_VALUES, "name.givenName", ImmutableList.of("Pippo"));
        LscDatasetModification lastname = new LscDatasetModification(ADD_VALUES, "name.familyName", ImmutableList.of("Pezzotto"));
        LscDatasetModification email = new LscDatasetModification(ADD_VALUES, "emails[]", ImmutableList.of("pippo@localhost.com"));
        lm.setLscAttributeModifications(ImmutableList.of(username, password, firstname, lastname, email));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean("pippo", lscDatasets, true);
        assertThat(bean).isNotNull();
    }

    @Test
    @Order(3)
    void updateNestedAttribute() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(task);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("pippo");
        LscDatasetModification datasetModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "name.givenName", ImmutableList.of("Tizio"));
        lm.setLscAttributeModifications(ImmutableList.of(datasetModification));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean("pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("name.givenName")).isEqualTo("Tizio");
    }

    @Test
    @Order(4)
    void updateMultivalueAttribute() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(task);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean destinationBean = testDstService.getBean("pippo", lscDatasets, true);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("pippo");
        lm.setDestinationBean(destinationBean);
        LscDatasetModification datasetModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "emails[]", ImmutableList.of("other@localhost.com"));
        lm.setLscAttributeModifications(ImmutableList.of(datasetModification));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        IBean bean = testDstService.getBean("pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("emails[]")).isEqualTo("other@localhost.com");        
    }

    @Test
    @Order(5)
    void updateMultivalueWithPathAttribute() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(task);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean destinationBean = testDstService.getBean("pippo", lscDatasets, true);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("pippo");
        lm.setDestinationBean(destinationBean);
        LscDatasetModification datasetModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "emails[type eq work]", ImmutableList.of("work@localhost.com"));
        lm.setLscAttributeModifications(ImmutableList.of(datasetModification));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        IBean bean = testDstService.getBean("pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("emails[type eq work]")).isEqualTo("work@localhost.com");        
    }
    
    @Test
    @Order(6)
    void updateExtendedSchemaAttribute() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(task);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("pippo");
        LscDatasetModification datasetModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "ENTERPRISE_USER.department", ImmutableList.of("IT"));
        lm.setLscAttributeModifications(ImmutableList.of(datasetModification));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean("pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("ENTERPRISE_USER.department")).isEqualTo("IT");        
    }
    
    @Test
    @Order(7)
    void removeUser() throws LscServiceException {
        testDstService = new ScimDstService(task);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean("pippo", lscDatasets, true);
        assertThat(bean).isNotNull();
        LscModifications lm = new LscModifications(LscModificationType.DELETE_OBJECT);
        lm.setMainIdentifer("pippo");
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        bean = testDstService.getBean("pippo", lscDatasets, true);
        assertThat(bean).isNull();        
    }
    
    @Test
    @Order(8)
    void addGroup() throws LscServiceException {
        when(serviceSettings.getEntity()).thenReturn("Groups");
        when(serviceSettings.getPivot()).thenReturn("displayName");
        when(serviceSettings.getSourcePivot()).thenReturn("cn");
        testDstService = new ScimDstService(task);
        LscModifications lm = new LscModifications(LscModificationType.CREATE_OBJECT);
        lm.setMainIdentifer("developer");
        LscDatasetModification displayName = new LscDatasetModification(ADD_VALUES, "displayName", ImmutableList.of("developer"));
        lm.setLscAttributeModifications(ImmutableList.of(displayName));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("cn", "developer");
        IBean bean = testDstService.getBean("developer", lscDatasets, true);
        assertThat(bean).isNotNull();
    }

    @Test
    @Order(9)
    void updateMembership() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(task);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("developer");
        String adminUser = "{\"display\": \"admin\" }";
        LscDatasetModification members = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members[]", ImmutableList.of(adminUser));    
        lm.setLscAttributeModifications(ImmutableList.of(members));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("cn", "developer");
        IBean bean = testDstService.getBean("developer", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("members[display eq admin]")).isNotNull();
    }
    
    @Test
    @Order(10)
    void removeGroup() throws LscServiceException {
        testDstService = new ScimDstService(task);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("cn", "developer");
        IBean bean = testDstService.getBean("developer", lscDatasets, true);
        assertThat(bean).isNotNull();
        LscModifications lm = new LscModifications(LscModificationType.DELETE_OBJECT);
        lm.setMainIdentifer("developer");
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        bean = testDstService.getBean("developer", lscDatasets, true);
        assertThat(bean).isNull();
    }

    @Test
    @Order(11)
    void constructorWithoutSettingsShouldFail() throws LscServiceException {
        when(pluginDestinationService.getAny()).thenReturn(null);
        ScimDstService testDstService;
        try {
            testDstService = new ScimDstService(task);
        } catch (LscServiceConfigurationException e) {
            testDstService = null;
        }
        assertThat(testDstService).isNull();
        when(pluginDestinationService.getAny()).thenReturn(ImmutableList.of(serviceSettings));
    }
    
    @Test
    @Order(12)
    void constructorWithIncorrectSettingsShouldFail() throws LscServiceException {
        when(serviceSettings.getEntity()).thenReturn("Utenti");
        ScimDstService testDstService;
        try {
            testDstService = new ScimDstService(task);
        } catch (LscServiceConfigurationException e) {
            testDstService = null;
        }
        assertThat(testDstService).isNull();
        when(serviceSettings.getEntity()).thenReturn("Users");
    }

    @Test
    @Order(13)
    void constructorWithoutConnectionSettingsShouldFail() throws LscServiceException {
        when(pluginDestinationService.getConnection().getReference()).thenReturn(null);
        ScimDstService testDstService;
        try {        
            testDstService = new ScimDstService(task);
        } catch (LscServiceConfigurationException e) {
            testDstService = null;
        }
        assertThat(testDstService).isNull();
    }

}
