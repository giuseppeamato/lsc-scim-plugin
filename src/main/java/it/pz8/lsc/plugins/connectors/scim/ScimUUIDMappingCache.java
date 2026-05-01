package it.pz8.lsc.plugins.connectors.scim;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.lsc.configuration.DatabaseConnectionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import it.pz8.lsc.plugins.connectors.scim.bean.CachedData;
import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;
import it.pz8.lsc.plugins.connectors.scim.utils.ScimUtils;

/**
 * @author Giuseppe Amato
 *
 */
public class ScimUUIDMappingCache {

	private static final Logger LOGGER = LoggerFactory.getLogger(ScimUUIDMappingCache.class);
	
	private final HikariDataSource dataSource;
	private final boolean writeEnabled;
	private enum FILTER_FIELDS {PIVOT, SOURCE_UUID};
	
	public ScimUUIDMappingCache(ScimServiceSettings settings) {
		LOGGER.debug("Init service");
		if (settings.getCacheConnection()!=null) {
	        DatabaseConnectionType dbConnection = (DatabaseConnectionType)settings.getCacheConnection().getReference();
	       	writeEnabled = settings.getCacheConnection().isWriteEnabled();
        	HikariConfig config = new HikariConfig();
        	config.setJdbcUrl(dbConnection.getUrl());
        	config.setUsername(dbConnection.getUsername());
        	config.setPassword(dbConnection.getPassword());
        	dataSource = new HikariDataSource(config);
        	initSchema();
        	ScimUtils.setCache(this);
		} else {
			writeEnabled = false;
			dataSource = null;
		}
    }

    public boolean isWriteEnabled() {
		return writeEnabled;
	}

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    private void initSchema() {
        try (var conn = getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS MAPPING (
                    PIVOT VARCHAR NOT NULL,
                    ENTITY VARCHAR NOT NULL,
                    SOURCE_UUID VARCHAR,
                    SCIM_ID VARCHAR,
                    PRIMARY KEY (PIVOT, ENTITY)
                )
            """);
        } catch (SQLException e) {
            throw new IllegalStateException("Schema init failed", e);
        }
    }
    
    public void saveMapping(String pivot, String sourceUUIDValue, String scimId, String entity) {
    	LOGGER.debug("saveMapping pivot:{} sourceUUID:{} scimId:{} entity:{}", pivot, sourceUUIDValue, scimId, entity);
    	try (var conn = dataSource.getConnection(); 
				var ps = conn.prepareStatement("MERGE INTO MAPPING (PIVOT, ENTITY, SOURCE_UUID, SCIM_ID) VALUES (?, ?, ?, ?)")) {
		    ps.setString(1, pivot);
		    ps.setString(2, entity);
		    ps.setString(3, sourceUUIDValue);
		    ps.setString(4, scimId);
		    ps.executeUpdate();
    	} catch (SQLException e) {
    		logError(e);
    		throw new IllegalStateException(e);
    	}
    }
    
    public void saveSourceUUID(String pivot, String sourceUUIDValue, String entity) {
    	saveMapping(pivot, sourceUUIDValue, null, entity);
    }
    
    public void updateScimId(String pivot, String scimId, String entity) {
    	LOGGER.debug("updateScimId pivot:{} scimId:{} entity:{}", pivot, scimId, entity);
    	try (var conn = dataSource.getConnection(); 
				var ps = conn.prepareStatement("UPDATE MAPPING SET SCIM_ID=? WHERE PIVOT = ? AND ENTITY = ?")) {
		    ps.setString(1, scimId);
		    ps.setString(2, pivot);
		    ps.setString(3, entity);
		    ps.executeUpdate();
    	} catch (SQLException e) {
    		logError(e);
    		throw new IllegalStateException(e);
    	}
    }
    
    public CachedData getCachedData(String filterName, String filterValue, String entity) {
    	LOGGER.debug("getCachedData filter:{}={} entity:{}", filterName, filterValue, entity);
    	CachedData cachedData = null;
    	String sql;
    	if (filterName.equals(FILTER_FIELDS.PIVOT)) {
    		sql = "SELECT PIVOT, SOURCE_UUID, SCIM_ID FROM MAPPING WHERE lower(PIVOT) = ? AND ENTITY = ?";
    	} else {
    		sql = "SELECT PIVOT, SOURCE_UUID, SCIM_ID FROM MAPPING WHERE lower(SOURCE_UUID) = ? AND ENTITY = ?";
    	}
    	try (var conn = dataSource.getConnection(); 
				var ps = conn.prepareStatement(sql)) {
		    ps.setString(1, filterValue.toLowerCase());
		    ps.setString(2, entity);
		    try (ResultSet rs = ps.executeQuery()) {
		    	if (rs.next()) {
		    		cachedData = new CachedData(rs.getString(1), rs.getString(2), rs.getString(3), entity);
		    	}
		    }
		    return cachedData;
    	} catch (SQLException e) {
    		logError(e);
    		throw new IllegalStateException(e);
    	}
    }
    
    private void logError(SQLException e) {
        LOGGER.error("""
				Error executing SQL command.
				Message: {}
				SQLState: {}
				Error Code: {}
				""", e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
    }
}