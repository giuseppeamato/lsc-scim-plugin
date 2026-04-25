package it.pz8.lsc.plugins.connectors.scim.rs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.ClientCredentialsGrant;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.AccessToken;

/**
 * @author Giuseppe Amato
 *
 */
public class Oauth2TokenProvider implements TokenProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(Oauth2TokenProvider.class);
	
	private TokenRequest request;
	private volatile AccessToken currentToken;
	private volatile long tokenObtainedTime = 0;	
    private final ReentrantLock lock = new ReentrantLock();
    
    public Oauth2TokenProvider(String clientId, String clientSecret, String scope, URI tokenEndpoint) throws URISyntaxException {
    	LOGGER.debug("Init Oauth2 Token Provider");
        ClientCredentialsGrant grant = new ClientCredentialsGrant();
        ClientAuthentication clientAuth = new ClientSecretBasic(new ClientID(clientId), new Secret(clientSecret));
        request = new TokenRequest(tokenEndpoint, clientAuth, grant, new Scope(Scope.parse(scope)));
    }
    
    @Override
    public String getToken() throws Exception {
        if (currentToken != null && !isTokenExpired()) {
            return currentToken.getValue();
        }
        lock.lock();
        try {
            if (currentToken == null || isTokenExpired()) {
                refreshToken();
            }
            return currentToken.getValue();
        } finally {
            lock.unlock();
        }
    }
    
    private boolean isTokenExpired() {
        if (currentToken == null) return true;
        long now = System.currentTimeMillis() / 1000;
        long expiresIn = currentToken.getLifetime();
        long expireAt = (tokenObtainedTime + expiresIn - 30);
        return now>=expireAt;
    }

    private void refreshToken() throws Exception {
    	LOGGER.debug("Request new token.");
        TokenResponse response = TokenResponse.parse(request.toHTTPRequest().send());
        if (response.indicatesSuccess()) {
        	LOGGER.debug("Token received.");
        	AccessTokenResponse successResponse = response.toSuccessResponse();
        	currentToken = successResponse.getTokens().getAccessToken();
        	tokenObtainedTime = System.currentTimeMillis() / 1000;
        } else {
            HTTPResponse errorResponse = response.toHTTPResponse();            
            LOGGER.error("Error while getting token: {} {}", errorResponse.getStatusCode(), errorResponse.getBody());
        }
    }
}
