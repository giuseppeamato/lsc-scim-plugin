package it.pz8.lsc.plugins.connectors.scim.utils;

import it.pz8.lsc.plugins.connectors.scim.ScimUUIDMappingCache;
import it.pz8.lsc.plugins.connectors.scim.bean.CachedData;

/**
 * @author Giuseppe Amato
 *
 */
public final class ScimUtils {
	
	private static volatile ScimUUIDMappingCache cache;

	private ScimUtils() {
	}
	
	public static void setCache(ScimUUIDMappingCache cache) {
		ScimUtils.cache = cache;
	}
	
    public static CachedData getCachedDataByPivot(String pivotValue, String entity) {
    	return cache.getCachedData("PIVOT", pivotValue, entity);
    }

    public static CachedData getCachedDataByUUID(String uuid, String entity) {
    	return cache.getCachedData("SOURCE_UUID", uuid, entity);
    }

}
