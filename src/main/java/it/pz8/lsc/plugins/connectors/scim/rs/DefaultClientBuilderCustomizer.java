package it.pz8.lsc.plugins.connectors.scim.rs;

import javax.ws.rs.client.ClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultClientBuilderCustomizer implements ClientBuilderCustomizer {

	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClientBuilderCustomizer.class);
	
	@Override
	public void customize(ClientBuilder clientBuilder) {
		if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Default implementation has no customization");
        }
	}

}
