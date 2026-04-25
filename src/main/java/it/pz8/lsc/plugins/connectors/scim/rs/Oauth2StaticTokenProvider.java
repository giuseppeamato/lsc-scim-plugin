package it.pz8.lsc.plugins.connectors.scim.rs;

/**
 * @author Giuseppe Amato
 *
 */
public class Oauth2StaticTokenProvider implements TokenProvider  {

    private final String accessToken;

    public Oauth2StaticTokenProvider(String accessToken) {
        this.accessToken = accessToken;
    }
	
	@Override
	public String getToken() throws Exception {
		return this.accessToken;
	}

}
