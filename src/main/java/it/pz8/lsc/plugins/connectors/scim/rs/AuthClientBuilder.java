package it.pz8.lsc.plugins.connectors.scim.rs;

import java.net.URISyntaxException;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.exception.LscServiceConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.pz8.lsc.plugins.connectors.scim.generated.Oauth2ConnectionSettings;

/**
 * @author Giuseppe Amato
 *
 */
public class AuthClientBuilder {

	protected static final Logger LOGGER = LoggerFactory.getLogger(AuthClientBuilder.class);
	
    private static final List<ClientBuilderCustomizer> CLIENT_CUSTOMIZERS = 
    		StreamSupport.stream(ServiceLoader.load(ClientBuilderCustomizer.class).spliterator(), false).toList();
    
    private AuthClientBuilder() {
    }
    
    public static Client build(PluginConnectionType connection) throws LscServiceConfigurationException {
    	ClientBuilder clientBuilder = ClientBuilder.newBuilder().property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
        if (connection.getAny()!=null && !connection.getAny().isEmpty()) {
        	Oauth2ConnectionSettings config = (Oauth2ConnectionSettings)connection.getAny().get(0);
			try {
				clientBuilder.register(new Oauth2Authenticator(TokenProviderFactory.from(config))); 
			} catch (URISyntaxException e) {
				throw new LscServiceConfigurationException(e);
			}
        } else {
        	clientBuilder.register(new BasicAuthenticator(connection.getUsername(), connection.getPassword()));
        }
        CLIENT_CUSTOMIZERS.forEach(c -> c.customize(clientBuilder));
        return clientBuilder.build();
    }

}
