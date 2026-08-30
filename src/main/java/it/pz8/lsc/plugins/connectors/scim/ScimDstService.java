package it.pz8.lsc.plugins.connectors.scim;

import static it.pz8.lsc.plugins.connectors.scim.ScimDao.GROUPS;
import static it.pz8.lsc.plugins.connectors.scim.ScimDao.USERS;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;

import org.apache.commons.lang3.StringUtils;
import org.lsc.LscDatasets;
import org.lsc.LscModifications;
import org.lsc.Task;
import org.lsc.beans.IBean;
import org.lsc.configuration.ConnectionType;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.TaskType;
import org.lsc.configuration.ValuesType;
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
    public ScimDstService(final TaskType task) throws LscServiceConfigurationException {
        try {
            if (task.getPluginDestinationService().getAny() == null || task.getPluginDestinationService().getAny().size() != 1 || !(task.getPluginDestinationService().getAny().get(0) instanceof ScimServiceSettings)) {
                throw new LscServiceConfigurationException("Unable to identify the scim service configuration inside the plugin destination node of the task: " + task.getName());
            }
            settings = (ScimServiceSettings) task.getPluginDestinationService().getAny().get(0);
            if (StringUtils.isBlank(settings.getEntity()) || (!settings.getEntity().equals(USERS) && !settings.getEntity().equals(GROUPS))) {
                throw new LscServiceConfigurationException("Incorrect entity setting.");
            }
            PluginConnectionType pluginConnectionType = (PluginConnectionType) task.getPluginDestinationService().getConnection().getReference();
            if (pluginConnectionType == null) {
                throw new LscServiceConfigurationException("Unable to identify the scim connection settings inside the connection node of the task: " + task.getName());
            }
            if (settings.getCacheConnection()!=null && settings.getCacheConnection().getReference()!=null && settings.getCacheConnection().isWriteEnabled() && StringUtils.isBlank(settings.getSourceUUID())) {
           		throw new LscServiceConfigurationException("Unable to identify the source UUID attribute, it is mandatory when mapping cache is enabled inside the plugin destination node of the task: " + task.getName());	
            }
            beanClass = (Class<IBean>) Class.forName(task.getBean());
            dao = new ScimDao(pluginConnectionType, settings);
        } catch (ClassNotFoundException e) {
            throw new LscServiceConfigurationException(e);
        }
    }

    @Override
    public Map<String, LscDatasets> getListPivots(Task task) throws LscServiceException {
        LOGGER.debug("Call to Destination getListPivots");
        try {
            return dao.getList();
        } catch (Exception e) {
            throw new LscServiceCommunicationException(String.format("Error while getting pivot list (%s)", e), e);
        }
    }

    @Override
    public IBean getBean(Task task, String pivotRawValue, LscDatasets lscDatasets, boolean fromSameService) throws LscServiceException {
    	LOGGER.debug("Call to getBean({}, {}, {})", pivotRawValue, lscDatasets, fromSameService);
        String pivotValue = lscDatasets.getStringValueAttribute(dao.getSourcePivotName());
        String sourceUUIDValue = (settings.getSourceUUID()==null) ? null : lscDatasets.getStringValueAttribute(settings.getSourceUUID());
        try {
            Map<String, Object> entity = dao.getDetailsByPivot(pivotValue, sourceUUIDValue);
            IBean bean = beanClass.getDeclaredConstructor().newInstance();
            bean.setMainIdentifier(pivotValue);        
            LscDatasets datasets = new LscDatasets();
            entity.entrySet().forEach(entry -> datasets.put(entry.getKey(), entry.getValue()==null ? new LinkedHashSet<>() : entry.getValue()));
            bean.setDatasets(datasets);
            return bean;
        } catch (NotFoundException e) {
            LOGGER.debug("id {} not found", pivotValue);
            return null;
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new LscServiceException(String.format("Error while creating the instance of task bean %s", beanClass.getName()), e);
        } catch (ProcessingException e) {
            throw new LscServiceException(String.format("Exception while getting bean with id %s (%s)", pivotValue, e), e);
        }
    }

    @Override
    public boolean apply(LscModifications lm) throws LscServiceException {
        boolean result = false;
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
            	LOGGER.debug("Creating SCIM entry: {}", lm.getMainIdentifier());
                result = dao.create(lm);
                break;
            case UPDATE_OBJECT:
            	LOGGER.debug("Updating SCIM entry: {}", lm.getMainIdentifier());
                result = dao.update(lm);
                break;
            case DELETE_OBJECT:
            	LOGGER.debug("Deleting SCIM entry: {}", lm.getMainIdentifier());
                result = dao.delete(lm.getMainIdentifier());
                break;
            default:
                LOGGER.error("Unknown operation {}", lm.getOperation());
                result = false;
            }
        }
        return result;
    }

    @Override
    public List<String> getWriteDatasetIds() {
        LOGGER.debug("Call to getWriteDatasetIds()");
        ValuesType writableAttrs = settings.getWritableAttributes();
        return (writableAttrs!=null)?writableAttrs.getString():null;
    }

	@Override
	public Collection<Class<? extends ConnectionType>> getSupportedConnectionType() {
		ArrayList<Class<? extends ConnectionType>> supportedConnectionTypes = new ArrayList<>();
		supportedConnectionTypes.add(PluginConnectionType.class);
		return supportedConnectionTypes;
	}

}
