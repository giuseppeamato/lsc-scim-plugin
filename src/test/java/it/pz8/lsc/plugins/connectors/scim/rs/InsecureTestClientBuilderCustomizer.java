package it.pz8.lsc.plugins.connectors.scim.rs;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.ClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InsecureTestClientBuilderCustomizer implements ClientBuilderCustomizer {

	private static final Logger LOGGER = LoggerFactory.getLogger(InsecureTestClientBuilderCustomizer.class);
	
	@Override
	public void customize(ClientBuilder clientBuilder) {
        LOGGER.warn("Disable SSL certificate verification");
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { LOGGER.debug("Do nothing. Only for test."); }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { LOGGER.debug("Do nothing. Only for test."); }
                }
            }, new SecureRandom());
            clientBuilder
            		.sslContext(sslContext)
            		.hostnameVerifier((hostname, session) -> true);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOGGER.error("Failed to disable SSL verification", e);
        }
	}

}
