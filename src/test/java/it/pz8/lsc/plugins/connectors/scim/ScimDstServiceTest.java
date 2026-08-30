package it.pz8.lsc.plugins.connectors.scim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lsc.LscDatasetModification.LscDatasetModificationType.ADD_VALUES;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.lsc.LscDatasetModification;
import org.lsc.LscDatasetModification.LscDatasetModificationType;
import org.lsc.LscDatasets;
import org.lsc.LscModificationType;
import org.lsc.LscModifications;
import org.lsc.Task;
import org.lsc.beans.IBean;
import org.lsc.configuration.ConnectionType;
import org.lsc.configuration.DatabaseConnectionType;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.PluginDestinationServiceType;
import org.lsc.configuration.ServiceType;
import org.lsc.configuration.TaskType;
import org.lsc.exception.LscServiceCommunicationException;
import org.lsc.exception.LscServiceConfigurationException;
import org.lsc.exception.LscServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import it.pz8.lsc.plugins.connectors.scim.bean.CachedData;
import it.pz8.lsc.plugins.connectors.scim.bean.OperationType;
import it.pz8.lsc.plugins.connectors.scim.generated.NamespaceType;
import it.pz8.lsc.plugins.connectors.scim.generated.SchemasType;
import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;
import it.pz8.lsc.plugins.connectors.scim.utils.ScimUtils;

/**
 * @author Giuseppe Amato
 *
 */
@TestMethodOrder(OrderAnnotation.class)
class ScimDstServiceTest {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ScimDstServiceTest.class);

    private static final int EXPOSED_PORT = 9443;
    private static final String IMAGE_NAME = "wso2/wso2is:7.2.0-alpine";
    private static final int TIMEOUT = 300;
    private static final String SCIM_BASEPATH = "https://localhost:%d/scim2";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String CACHEDB_PATH = "jdbc:h2:file:./data/scim2mapping";
    private static final String CACHEDB_USERNAME = "sa";
    private static final String CACHEDB_PASSWORD = "admin";
    private static final String CACHEDB_DRIVER = "org.h2.Driver";

    private static int mappedPort;
    private static GenericContainer<?> wso2ids;

    private static TaskType taskType;
    private static Task task = null;
    private static ScimServiceSettings serviceSettings;
    private static DatabaseConnectionType dbConnectionType;
    private static PluginConnectionType connectionType;
    private static PluginDestinationServiceType pluginDestinationService;
    private static ScimDstService testDstService;

    @BeforeAll
    static void setup() {
        wso2ids = new GenericContainer<>(IMAGE_NAME);
        wso2ids.withExposedPorts(EXPOSED_PORT);
        wso2ids.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(TIMEOUT)));
        wso2ids.withLogConsumer(new Slf4jLogConsumer(LOGGER));
        wso2ids.start();

        mappedPort = wso2ids.getMappedPort(EXPOSED_PORT);
        LOGGER.info("Mapped port: {}:{}", mappedPort, EXPOSED_PORT);
    }

    @BeforeEach
    void testSetup() {
        pluginDestinationService = mock(PluginDestinationServiceType.class);
        serviceSettings = mock(ScimServiceSettings.class);
        taskType = mock(TaskType.class);
        connectionType = mock(PluginConnectionType.class);
        ServiceType.Connection connection = mock(ServiceType.Connection.class);
        when(connectionType.getUrl()).thenReturn(String.format(SCIM_BASEPATH, mappedPort));
        when(connectionType.getUsername()).thenReturn(USERNAME);
        when(connectionType.getPassword()).thenReturn(PASSWORD);
        when(connection.getReference()).thenReturn(connectionType);
        when(pluginDestinationService.getConnection()).thenReturn(connection);
        when(pluginDestinationService.getAny()).thenReturn(List.of(serviceSettings));
        when(serviceSettings.getEntity()).thenReturn(ScimDao.USERS);
        when(serviceSettings.getSchema()).thenReturn(createScimSchema());
        when(serviceSettings.getFilter()).thenReturn(null);
        when(serviceSettings.getPivot()).thenReturn("userName");
        when(serviceSettings.getSourcePivot()).thenReturn("uid");

        dbConnectionType = mock(DatabaseConnectionType.class);
        ScimServiceSettings.CacheConnection cacheconnection = mock(ScimServiceSettings.CacheConnection.class);
        when(dbConnectionType.getUrl()).thenReturn(CACHEDB_PATH);
        when(dbConnectionType.getUsername()).thenReturn(CACHEDB_USERNAME);
        when(dbConnectionType.getPassword()).thenReturn(CACHEDB_PASSWORD);
        when(dbConnectionType.getDriver()).thenReturn(CACHEDB_DRIVER);
        when(cacheconnection.getReference()).thenReturn(dbConnectionType);
        when(cacheconnection.isWriteEnabled()).thenReturn(true);
        when(serviceSettings.getCacheConnection()).thenReturn(cacheconnection);
        when(serviceSettings.getSourceUUID()).thenReturn("entryDN");

        when(serviceSettings.getAttributes()).thenReturn(null);
        when(serviceSettings.getExcludedAttributes()).thenReturn(null);
        when(taskType.getBean()).thenReturn("org.lsc.beans.SimpleBean");
        when(taskType.getPluginDestinationService()).thenReturn(pluginDestinationService);
    }

    static SchemasType createScimSchema() {
        List<NamespaceType> nsList = new ArrayList<>();
        NamespaceType ns = new NamespaceType();
        ns.setAlias("ENTERPRISE_USER_SCHEMA");
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
        testDstService = new ScimDstService(taskType);
        Map<String, LscDatasets> bean = testDstService.getListPivots(task);
        assertThat(bean).isNotNull().isNotEmpty();
    }

    @Test
    @Order(2)
    void changeIdShouldNotFail() throws LscServiceException {
        testDstService = new ScimDstService(taskType);
        LscModifications mod = new LscModifications(LscModificationType.CHANGE_ID);
        mod.setMainIdentifer("pippo");
        boolean result = testDstService.apply(mod);
        assertThat(result).isTrue();
    }

    @Test
    @Order(3)
    void modificationWithoutMainIdShouldFail() throws LscServiceException {
        testDstService = new ScimDstService(taskType);
        LscModifications mod = new LscModifications(LscModificationType.CHANGE_ID);
        boolean result = testDstService.apply(mod);
        assertThat(result).isFalse();
    }

    @Test
    @Order(4)
    void addUser() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(taskType);
        LscModifications lm = new LscModifications(LscModificationType.CREATE_OBJECT);
        lm.setMainIdentifer("pippo");
        LscDatasetModification username = new LscDatasetModification(ADD_VALUES, "userName", List.of("pippo"));
        LscDatasetModification password = new LscDatasetModification(ADD_VALUES, "password", List.of("123456Aa!"));
        LscDatasetModification firstname = new LscDatasetModification(ADD_VALUES, "name.givenName", List.of("Pippo"));
        LscDatasetModification lastname = new LscDatasetModification(ADD_VALUES, "name.familyName", List.of("Pezzotto"));
        LscDatasetModification email = new LscDatasetModification(ADD_VALUES, "emails[]", List.of("pippo@localhost.com"));
        LscDatasetModification workemail = new LscDatasetModification(ADD_VALUES, "emails[type eq \"work\"]", List.of("pippo@acme.com"));
        lm.setLscAttributeModifications(List.of(username, password, firstname, lastname, email, workemail));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean).isNotNull();
    }

    @Test
    @Order(5)
    void updateNestedAttribute() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(taskType);
        List<String> writableDatasetIds = testDstService.getWriteDatasetIds();
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("pippo");
        LscDatasetModification datasetModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "name.givenName", List.of("Tizio"));
        lm.setLscAttributeModifications(List.of(datasetModification));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("name.givenName")).isEqualTo("Tizio");
        assertThat(writableDatasetIds==null || writableDatasetIds.contains("name.givenName")).isTrue();
    }

    @Test
    @Order(6)
    void updateMultivalueWithPathAttribute() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(taskType);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean destinationBean = testDstService.getBean(task, "pippo", lscDatasets, true);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("pippo");
        lm.setDestinationBean(destinationBean);
        LscDatasetModification datasetModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "emails[type eq \"work\"]", List.of("dev@acme.com"));
        lm.setLscAttributeModifications(List.of(datasetModification));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        IBean bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("emails[type eq \"work\"]")).isEqualTo("dev@acme.com");
    }

    @Test
    @Order(7)
    void updateMultivalueAttribute() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(taskType);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean destinationBean = testDstService.getBean(task, "pippo", lscDatasets, true);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("pippo");
        lm.setDestinationBean(destinationBean);
        LscDatasetModification datasetModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "emails[]", List.of("other@localhost.com"));
        lm.setLscAttributeModifications(List.of(datasetModification));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        IBean bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("emails[]")).isEqualTo("other@localhost.com");
    }

    @Test
    @Order(8)
    void updateExtendedSchemaAttribute() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(taskType);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("pippo");
        LscDatasetModification datasetModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "ENTERPRISE_USER_SCHEMA.department", List.of("IT"));
        lm.setLscAttributeModifications(List.of(datasetModification));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("ENTERPRISE_USER_SCHEMA.department")).isEqualTo("IT");
    }

    @Test
    @Order(9)
    void removeAttribute() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(taskType);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("pippo");
        LscDatasetModification datasetModification = new LscDatasetModification(LscDatasetModificationType.DELETE_VALUES, "ENTERPRISE_USER_SCHEMA.department", List.of("IT"));
        lm.setLscAttributeModifications(List.of(datasetModification));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("ENTERPRISE_USER_SCHEMA.department")).isBlank();
    }

    @Test
    @Order(10)
    void addAttribute() throws LscServiceException, NamingException {
        testDstService = new ScimDstService(taskType);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("pippo");
        LscDatasetModification datasetModification = new LscDatasetModification(LscDatasetModificationType.ADD_VALUES, "ENTERPRISE_USER_SCHEMA.department", List.of("HR"));
        lm.setLscAttributeModifications(List.of(datasetModification));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("ENTERPRISE_USER_SCHEMA.department")).isEqualTo("HR");
    }

    @Test
    @Order(11)
    void getDetailsByPivotsWithAttributes() throws LscServiceException, NamingException {
        when(serviceSettings.getAttributes()).thenReturn("username,ENTERPRISE_USER_SCHEMA.department");
        testDstService = new ScimDstService(taskType);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("ENTERPRISE_USER_SCHEMA.department")).isEqualTo("HR");
        assertThat(bean.getDatasetFirstValueById("name.familyName")).isBlank();
    }

    @Test
    @Order(12)
    void getDetailsByPivotsWithExcludedAttributes() throws LscServiceException, NamingException {
        when(serviceSettings.getExcludedAttributes()).thenReturn("ENTERPRISE_USER_SCHEMA.department");
        testDstService = new ScimDstService(taskType);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("ENTERPRISE_USER_SCHEMA.department")).isBlank();
        assertThat(bean.getDatasetFirstValueById("name.familyName")).isNotBlank();
    }

    @Test
    @Order(13)
    void getDetailsByPivotsWithoutValues() throws LscServiceException {
        testDstService = new ScimDstService(taskType);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "nobody");
        IBean bean = testDstService.getBean(task, "nobody", lscDatasets, true);
        assertThat(bean).isNull();
    }

    @Test
    @Order(14)
    void taskBeanWithoutPublicNoArgContructorShouldFail() throws LscServiceException {
        when(taskType.getBean()).thenReturn("java.lang.Integer");
        IBean bean = null;
        try {
            testDstService = new ScimDstService(taskType);
            LscDatasets lscDatasets = new LscDatasets();
            lscDatasets.put("uid", "admin");
            bean = testDstService.getBean(task, "admin", lscDatasets, true);
        } catch (LscServiceException e) {
            testDstService = null;
        }
        assertThat(bean).isNull();
    }

    @Test
    @Order(15)
    void taskWithIncorrectBeanClassShouldFail() throws LscServiceException {
        when(taskType.getBean()).thenReturn("java.lang.WrongClass");
        try {
            testDstService = new ScimDstService(taskType);
        } catch (LscServiceConfigurationException e) {
            testDstService = null;
        }
        assertThat(testDstService).isNull();
    }

    @Test
    @Order(16)
    void removeUser() throws LscServiceException {
        testDstService = new ScimDstService(taskType);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("uid", "pippo");
        IBean bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean).isNotNull();
        OperationType operation = OperationType.getFromName("remove");
        assertThat(operation).isEqualTo(OperationType.REMOVE);
        LscModifications lm = new LscModifications(LscModificationType.DELETE_OBJECT);
        lm.setMainIdentifer("pippo");
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        assertThat(bean).isNull();
    }

    @Test
    @Order(17)
    void addGroup() throws LscServiceException {
        when(serviceSettings.getEntity()).thenReturn("Groups");
        when(serviceSettings.getPivot()).thenReturn("displayName");
        when(serviceSettings.getSourcePivot()).thenReturn("cn");
        testDstService = new ScimDstService(taskType);
        LscModifications lm = new LscModifications(LscModificationType.CREATE_OBJECT);
        lm.setMainIdentifer("developer");
        LscDatasetModification displayName = new LscDatasetModification(ADD_VALUES, "displayName", List.of("developer"));
        lm.setLscAttributeModifications(List.of(displayName));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("cn", "developer");
        IBean bean = testDstService.getBean(task, "developer", lscDatasets, true);
        assertThat(bean).isNotNull();
    }

    @Test
    @Order(18)
    void updateMembership() throws LscServiceException, NamingException {
        when(serviceSettings.getEntity()).thenReturn(ScimDao.USERS);
        when(serviceSettings.getPivot()).thenReturn("userName");
        when(serviceSettings.getSourcePivot()).thenReturn("uid");
        when(serviceSettings.getSourceUUID()).thenReturn("uid");
        testDstService = new ScimDstService(taskType);
        LscDatasets lscUserDatasets = new LscDatasets();
        lscUserDatasets.put("uid", "admin");
        lscUserDatasets.put("entryDN", "uid=admin");
        IBean userBean = testDstService.getBean(task, "admin", lscUserDatasets, true);
        CachedData cachedData = ScimUtils.getCachedDataByPivot(userBean.getMainIdentifier(), ScimDao.USERS);
        String scimId = cachedData.getScimId();
        String sourceUUID = cachedData.getSourceUUID();
        assertThat(cachedData.getPivot()).isEqualTo(userBean.getMainIdentifier());
        assertThat(cachedData.getEntity()).isEqualTo(ScimDao.USERS);
        CachedData cachedDataByUUID = ScimUtils.getCachedDataByUUID(sourceUUID, ScimDao.USERS);
        assertThat(cachedData.getPivot()).isEqualTo(cachedDataByUUID.getPivot());

        when(serviceSettings.getEntity()).thenReturn("Groups");
        when(serviceSettings.getPivot()).thenReturn("displayName");
        when(serviceSettings.getSourcePivot()).thenReturn("cn");
        testDstService = new ScimDstService(taskType);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("developer");
        String adminUser = "{\"display\": \"admin\", \"value\": \""+scimId+"\" }";
        LscDatasetModification members = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members[]", List.of(adminUser));
        lm.setLscAttributeModifications(List.of(members));
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("cn", "developer");
        IBean bean = testDstService.getBean(task, "developer", lscDatasets, true);
        assertThat(bean.getDatasetFirstValueById("members[display eq \"admin\"]")).isNotNull();
    }

    @Test
    @Order(19)
    void removeGroup() throws LscServiceException {
        when(serviceSettings.getEntity()).thenReturn("Groups");
        when(serviceSettings.getPivot()).thenReturn("displayName");
        when(serviceSettings.getSourcePivot()).thenReturn("displayName");
        testDstService = new ScimDstService(taskType);
        LscDatasets lscDatasets = new LscDatasets();
        lscDatasets.put("displayName", "developer");
        IBean bean = testDstService.getBean(task, "developer", lscDatasets, true);
        assertThat(bean).isNotNull();
        LscModifications lm = new LscModifications(LscModificationType.DELETE_OBJECT);
        lm.setMainIdentifer("developer");
        boolean result = testDstService.apply(lm);
        assertThat(result).isTrue();
        bean = testDstService.getBean(task, "developer", lscDatasets, true);
        assertThat(bean).isNull();
    }

    @Test
    @Order(20)
    void constructorWithoutSettingsShouldFail() throws LscServiceException {
        when(pluginDestinationService.getAny()).thenReturn(null);
        try {
            testDstService = new ScimDstService(taskType);
        } catch (LscServiceConfigurationException e) {
            testDstService = null;
        }
        assertThat(testDstService).isNull();
        when(pluginDestinationService.getAny()).thenReturn(List.of(serviceSettings));
    }

    @Test
    @Order(21)
    void constructorWithIncorrectSettingsShouldFail() throws LscServiceException {
        when(serviceSettings.getEntity()).thenReturn("Utenti");
        try {
            testDstService = new ScimDstService(taskType);
        } catch (LscServiceConfigurationException e) {
            testDstService = null;
        }
        assertThat(testDstService).isNull();
        when(serviceSettings.getEntity()).thenReturn(ScimDao.USERS);
    }

    @Test
    @Order(22)
    void constructorWithCacheWithoutSourceUUIDShouldFail() throws LscServiceException {
        when(serviceSettings.getSourceUUID()).thenReturn(null);
        try {
            testDstService = new ScimDstService(taskType);
        } catch (LscServiceConfigurationException e) {
            testDstService = null;
        }
        assertThat(testDstService).isNull();
    }

    @Test
    @Order(23)
    void constructorWithoutConnectionSettingsShouldFail() throws LscServiceException {
        when(pluginDestinationService.getConnection().getReference()).thenReturn(null);
        try {
            testDstService = new ScimDstService(taskType);
        } catch (LscServiceConfigurationException e) {
            testDstService = null;
        }
        assertThat(testDstService).isNull();
    }

    @Test
    @Order(24)
    void returnSupportedConnectionType() throws Exception {
        testDstService = new ScimDstService(taskType);
        Collection<Class<? extends ConnectionType>> supportedTypes = testDstService.getSupportedConnectionType();
        assertThat(supportedTypes).contains(PluginConnectionType.class);
    }

    @Test
    @Order(25)
    void getListPivotsUnauthenticatedShouldFail() throws LscServiceException {
        when(connectionType.getPassword()).thenReturn("");
        Map<String, LscDatasets> bean = null;
        try {
            testDstService = new ScimDstService(taskType);
            bean = testDstService.getListPivots(task);
        } catch (LscServiceCommunicationException e) {
            testDstService = null;
        }
        assertThat(bean).isNull();
    }

    @Test
    @Order(26)
    void getBeanUnauthenticatedShouldFail() throws LscServiceException {
        when(connectionType.getPassword()).thenReturn("");
        IBean bean = null;
        try {
            testDstService = new ScimDstService(taskType);
            LscDatasets lscDatasets = new LscDatasets();
            lscDatasets.put("uid", "pippo");
            bean = testDstService.getBean(task, "pippo", lscDatasets, true);
        } catch (LscServiceException e) {
            testDstService = null;
        }
        assertThat(bean).isNull();
    }

    @Test
    @Order(27)
    void addUserUnauthenticatedShouldFail() throws LscServiceException {
        when(connectionType.getPassword()).thenReturn("");
        testDstService = new ScimDstService(taskType);
        boolean result = false;
        try {
            LscModifications lm = new LscModifications(LscModificationType.CREATE_OBJECT);
            lm.setMainIdentifer("pippo");
            LscDatasetModification username = new LscDatasetModification(ADD_VALUES, "userName", List.of("pippo"));
            LscDatasetModification password = new LscDatasetModification(ADD_VALUES, "password", List.of("123456aA!"));
            LscDatasetModification firstname = new LscDatasetModification(ADD_VALUES, "name.givenName", List.of("Pippo"));
            LscDatasetModification lastname = new LscDatasetModification(ADD_VALUES, "name.familyName", List.of("Pezzotto"));
            LscDatasetModification email = new LscDatasetModification(ADD_VALUES, "emails[]", List.of("pippo@localhost.com"));
            lm.setLscAttributeModifications(List.of(username, password, firstname, lastname, email));
            result = testDstService.apply(lm);
        } catch (LscServiceCommunicationException e) {
            result = false;
        }
        assertThat(result).isFalse();
    }

    @Test
    @Order(28)
    void updateUserUnauthenticatedShouldFail() throws LscServiceException {
        when(connectionType.getPassword()).thenReturn("");
        testDstService = new ScimDstService(taskType);
        boolean result = false;
        try {
            LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
            lm.setMainIdentifer("pippo");
            LscDatasetModification email = new LscDatasetModification(ADD_VALUES, "emails[]", List.of("pezzotto@localhost.com"));
            lm.setLscAttributeModifications(List.of(email));
            result = testDstService.apply(lm);
        } catch (LscServiceCommunicationException e) {
            result = false;
        }
        assertThat(result).isFalse();
    }

}
