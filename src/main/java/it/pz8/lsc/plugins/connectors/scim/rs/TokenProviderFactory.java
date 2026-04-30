package it.pz8.lsc.plugins.connectors.scim.rs;

import java.net.URI;
import java.net.URISyntaxException;

import it.pz8.lsc.plugins.connectors.scim.generated.Oauth2ConnectionSettings;

/**
 * @author Giuseppe Amato
 *
 */
public class TokenProviderFactory {

	private TokenProviderFactory() {
	}
	
	public static TokenProvider from(Oauth2ConnectionSettings config) throws URISyntaxException {
	    return new Oauth2TokenProvider(config.getClientId(), config.getClientSecret(), config.getScope(), new URI(config.getTokenURL()));
	}
	
}
