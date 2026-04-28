package it.pz8.lsc.plugins.connectors.scim.rs;

import java.io.IOException;

/**
 * @author Giuseppe Amato
 *
 */
public interface TokenProvider {

	public String getToken() throws IOException;
	
}
