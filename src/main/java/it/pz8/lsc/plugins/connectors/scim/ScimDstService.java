package it.pz8.lsc.plugins.connectors.scim;

import static it.pz8.lsc.plugins.connectors.scim.ScimDao.GROUPS;
import static it.pz8.lsc.plugins.connectors.scim.ScimDao.USERS;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

import org.apache.commons.lang3.StringUtils;
import org.lsc.LscDatasets;
import org.lsc.LscModifications;
import org.lsc.beans.IBean;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.TaskType;
import org.lsc.exception.LscServiceCommunicationException;
import org.lsc.exception.LscServiceConfigurationException;
import org.lsc.exception.LscServiceException;
import org.lsc.service.IWritableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;

/**
 * @author Giuseppe Amato
 *
 */
public class ScimDstService implements IWritableService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ScimDstService.class);

    private final Class<IBean> beanClass;
    private final ScimServiceSettings settings;
    private final ScimDao dao;

    @SuppressWarnings("unchecked")
    public ScimDstService(final TaskType task) throws LscServiceConfigurationException, LscServiceCommunicationException {
        try {
            if (task.getPluginDestinationService().getAny() == null || task.getPluginDestinationService().getAny().size() != 1 || !((task.getPluginDestinationService().getAny().get(0) instanceof ScimServiceSettings))) {
                throw new LscServiceConfigurationException("Unable to identify the scim service configuration inside the plugin source node of the task: " + task.getName());
            }
            settings = (ScimServiceSettings) task.getPluginDestinationService().getAny().get(0);
            if (StringUtils.isBlank(settings.getEntity()) || (!settings.getEntity().equals(USERS) && !settings.getEntity().equals(GROUPS))) {
                throw new LscServiceConfigurationException("Incorrect entity setting.");
            }
            PluginConnectionType pluginConnectionType = (PluginConnectionType) task.getPluginDestinationService().getConnection().getReference();
            if (pluginConnectionType == null) {
                throw new LscServiceConfigurationException("Unable to identify the scim connection settings inside the connection node of the task: " + task.getName());
            }
            beanClass = (Class<IBean>) Class.forName(task.getBean());
            dao = new ScimDao(pluginConnectionType, settings);
        } catch (ClassNotFoundException e) {
            throw new LscServiceConfigurationException(e);
        }
    }

    @Override
    public Map<String, LscDatasets> getListPivots() throws LscServiceException {
        LOGGER.debug(String.format("Call to getListPivots"));
        try {
            return dao.getList();
        } catch (Exception e) {
            LOGGER.error(String.format("Error while getting pivot list (%s)", e));
            LOGGER.debug(e.toString(), e);
            throw new LscServiceCommunicationException(e);
        }
    }

    @Override
    public IBean getBean(String pivotRawValue, LscDatasets lscDatasets, boolean fromSameService) throws LscServiceException {
        LOGGER.debug(String.format("Call to getBean(%s, %s, %b)", pivotRawValue, lscDatasets, fromSameService));
        String pivotName = dao.getPivotName();
        String pivotValue = lscDatasets.getStringValueAttribute(dao.getSourcePivotName());
        try {
            Map<String, Object> entity = dao.getDetailsByPivot(pivotValue);
            IBean bean = beanClass.newInstance();
            bean.setMainIdentifier(entity.get(pivotName).toString());
            LscDatasets datasets = new LscDatasets();
            entity.entrySet().stream().forEach(entry -> datasets.put(entry.getKey(), entry.getValue()==null ? new LinkedHashSet<>() : entry.getValue()));
            bean.setDatasets(datasets);
            return bean;
        } catch (NotFoundException e) {
            LOGGER.debug(String.format("id %s not found", pivotValue));
            return null;
        } catch (ProcessingException | WebApplicationException e) {
            LOGGER.error(String.format("Exception while getting bean with id %s (%s)", pivotValue, e));
            LOGGER.error(e.toString(), e);
            throw new LscServiceException(e);
        } catch (InstantiationException | IllegalAccessException e) {
            LOGGER.error("Bad class name: " + beanClass.getName() + "(" + e + ")");
            LOGGER.debug(e.toString(), e);
            throw new LscServiceException(e);
        }
    }

    @Override
    public boolean apply(LscModifications lm) throws LscServiceException {
        boolean result = false;
        try {
            if (lm.getMainIdentifier() == null) {
                LOGGER.error("MainIdentifier is needed to update");
            } else {
                switch (lm.getOperation()) {
                case CHANGE_ID:
                    LOGGER.warn("Trying to change ID of SCIM entry, impossible operation, ignored.");
                    // Silently return without doing anything
                    result = true;
                    break;
                case CREATE_OBJECT:
                    LOGGER.debug("Creating SCIM entry: " + lm.getMainIdentifier());
                    result = dao.create(lm);
                    break;
                case UPDATE_OBJECT:
                    LOGGER.debug("Updating SCIM entry: " + lm.getMainIdentifier());
                    result = dao.update(lm);
                    break;
                case DELETE_OBJECT:
                    LOGGER.debug("Deleting SCIM entry: " + lm.getMainIdentifier());
                    dao.delete(lm.getMainIdentifier());
                    result = true;
                    break;
                default:
                    LOGGER.error(String.format("Unknown operation %s", lm.getOperation()));
                    result = false;
                }
            }
        } catch (NotFoundException e) {
            LOGGER.error(String.format("NotFoundException while writing (%s)", e));
            LOGGER.debug(e.toString(), e);
            result = false;
        } catch (ProcessingException e) {
            LOGGER.error(String.format("ProcessingException while writing (%s)", e));
            LOGGER.debug(e.toString(), e);
            result = false;
        }
        return result;
    }

    @Override
    public List<String> getWriteDatasetIds() {
        LOGGER.debug(String.format("Call to getWriteDatasetIds()"));
        return settings.getWritableAttributes().getString();
    }

}
