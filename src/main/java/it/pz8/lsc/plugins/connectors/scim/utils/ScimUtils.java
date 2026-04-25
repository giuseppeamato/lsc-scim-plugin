package it.pz8.lsc.plugins.connectors.scim.utils;

import it.pz8.lsc.plugins.connectors.scim.ScimUUIDMappingCache;

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
	
    public static String getScimId(String pivot, String entity) {
    	return cache.getScimId(pivot, entity);
    }

}
