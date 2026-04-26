package it.pz8.lsc.plugins.connectors.scim.rs;

import javax.ws.rs.client.ClientBuilder;

/**
 * @author Giuseppe Amato
 *
 */
@FunctionalInterface
public interface ClientBuilderCustomizer {

	void customize(ClientBuilder clientBuilder);
	
}
