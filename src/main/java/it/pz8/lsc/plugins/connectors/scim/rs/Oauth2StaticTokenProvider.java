package it.pz8.lsc.plugins.connectors.scim.rs;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Giuseppe Amato
 *
 */
public class Oauth2StaticTokenProvider implements TokenProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(Oauth2StaticTokenProvider.class);
	private String token;
	
    public Oauth2StaticTokenProvider(String token) {
    	LOGGER.debug("Init Oauth2 Static Token Provider");
    	this.token = token;
	}
    
	@Override
	public String getToken() throws IOException {
		return token;
	}

}
