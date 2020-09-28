package it.pz8.lsc.plugins.connectors.scim;

import static it.pz8.lsc.plugins.connectors.scim.ScimDao.GROUPS;
import static it.pz8.lsc.plugins.connectors.scim.ScimDao.ID;
import static it.pz8.lsc.plugins.connectors.scim.ScimDao.USERS;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

import org.apache.commons.lang3.StringUtils;
import org.lsc.LscDatasets;
import org.lsc.beans.IBean;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.TaskType;
import org.lsc.exception.LscServiceCommunicationException;
import org.lsc.exception.LscServiceConfigurationException;
import org.lsc.exception.LscServiceException;
import org.lsc.service.IService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;

/**
 * @author Giuseppe Amato
 *
 */
public class ScimSrcService implements IService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ScimSrcService.class);

    private final Class<IBean> beanClass;

    private final ScimDao dao;

    @SuppressWarnings("unchecked")
    public ScimSrcService(final TaskType task) throws LscServiceConfigurationException {
        try {
            if (task.getPluginSourceService().getAny() == null || task.getPluginSourceService().getAny().size() != 1 || !(task.getPluginSourceService().getAny().get(0) instanceof ScimServiceSettings)) {
                throw new LscServiceConfigurationException("Unable to identify the scim service configuration inside the plugin source node of the task: " + task.getName());
            }
            ScimServiceSettings settings = (ScimServiceSettings)task.getPluginSourceService().getAny().get(0);
            if (StringUtils.isBlank(settings.getEntity()) || (!settings.getEntity().equals(USERS) && !settings.getEntity().equals(GROUPS))) {
                throw new LscServiceConfigurationException("Incorrect entity setting.");
            }
            PluginConnectionType pluginConnectionType = (PluginConnectionType)task.getPluginSourceService().getConnection().getReference();
            if (pluginConnectionType == null) {
                throw new LscServiceConfigurationException("Unable to identify the scim connection settings inside the connection node of the task: " + task.getName());
            }
            beanClass = (Class<IBean>) Class.forName(task.getBean());
            dao = new ScimDao(pluginConnectionType, settings);
        } catch (Exception e) {
            throw new LscServiceConfigurationException(e);
        }
    }

    @Override
    public Map<String, LscDatasets> getListPivots() throws LscServiceException {
        LOGGER.debug("Call to getListPivots");
        try {
            return dao.getList();
        } catch (Exception e) {
            throw new LscServiceCommunicationException(String.format("Error while getting pivot list (%s)", e), e);
        }
    }

    @Override
    public IBean getBean(String pivotRawValue, LscDatasets lscDatasets, boolean fromSameService) throws LscServiceException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Call to getBean(%s, %s, %b)", pivotRawValue, lscDatasets, fromSameService));
        }
        if (lscDatasets.getAttributesNames().isEmpty()) {
            return null;
        }
        if (fromSameService) {
            return getBeanFromSameService(lscDatasets.getStringValueAttribute(ID));
        } else {
            return getBeanForClean(lscDatasets.getStringValueAttribute(dao.getPivotName()));
        }
    }

    private IBean getBeanFromSameService(String idValue) throws LscServiceException {
        if (idValue == null) {
            return null;
        }
        try {
            Map<String, Object> entity = dao.getDetails(idValue);
            IBean bean = beanClass.newInstance();
            bean.setMainIdentifier(idValue);
            LscDatasets datasets = new LscDatasets();
            entity.entrySet().stream().forEach(entry -> datasets.put(entry.getKey(), entry.getValue()==null?new LinkedHashSet<>():entry.getValue()));
            bean.setDatasets(datasets);
            return bean;
        } catch (NotFoundException e) {
            LOGGER.debug(String.format("id %s not found", idValue));
            return null;
        } catch (ProcessingException | WebApplicationException e) {
            throw new LscServiceException(String.format("Exception while getting bean with id %s (%s)", idValue, e), e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new LscServiceException(String.format("Bad class name: %s (%s)", beanClass.getName(), e), e);
        }
    }

    private IBean getBeanForClean(String pivotValue) throws LscServiceException {
        String pivotName = dao.getPivotName();
        try {
            Optional<Entry<String, LscDatasets>> entity = dao.findFirstByPivot(pivotValue);
            if (entity.isPresent()) {
                IBean bean = beanClass.newInstance();
                bean.setMainIdentifier(entity.get().getKey());
                bean.setDatasets(entity.get().getValue());
                return bean;
            } else {
                return null;
            }
        } catch (NotFoundException e) {
            LOGGER.debug(String.format("%s %s not found", pivotName, pivotValue));
            return null;
        } catch (ProcessingException | WebApplicationException e) {
            throw new LscServiceException(String.format("Exception while getting bean %s/%s (%s)", pivotName, pivotValue, e), e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new LscServiceException(String.format("Bad class name: %s (%s) ", beanClass.getName(), e), e);
        }
    }

}
