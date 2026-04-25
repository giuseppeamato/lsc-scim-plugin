package it.pz8.lsc.plugins.connectors.scim.rs;

/**
 * @author Giuseppe Amato
 *
 */
public interface TokenProvider {

	public String getToken() throws Exception;
	
}
