package it.pz8.lsc.plugins.connectors.scim.rs;

import java.net.URI;
import java.net.URISyntaxException;

import it.pz8.lsc.plugins.connectors.scim.generated.Oauth2ConnectionSettings;

/**
 * @author Giuseppe Amato
 *
 */
public class TokenProviderFactory {

	public static TokenProvider from(Oauth2ConnectionSettings config) throws URISyntaxException {
	    if (config.getToken() != null && !config.getToken().isEmpty()) {
	        return new Oauth2StaticTokenProvider(config.getToken());
	    } else {
	    	return new Oauth2TokenProvider(config.getClientId(), config.getClientSecret(), config.getScope(), new URI(config.getTokenURL()));
	    }
	}
	
}
