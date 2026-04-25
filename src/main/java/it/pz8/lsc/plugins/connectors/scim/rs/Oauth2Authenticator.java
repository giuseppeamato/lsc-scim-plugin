package it.pz8.lsc.plugins.connectors.scim.rs;

import java.io.IOException;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

/**
 * @author Giuseppe Amato
 *
 */
public class Oauth2Authenticator implements ClientRequestFilter {

	private final TokenProvider tokenProvider;

    public Oauth2Authenticator(TokenProvider tokenProvider) {
    	this.tokenProvider = tokenProvider;
    }
    
	@Override
	public void filter(ClientRequestContext requestContext) throws IOException {
        try {
            String token = tokenProvider.getToken();
            requestContext.getHeaders().putSingle("Authorization", "Bearer " + token);
        } catch (Exception e) {
            throw new IOException("Failed to obtain OAuth2 token", e);
        }
	}

}
