package it.pz8.lsc.plugins.connectors.scim.rs;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.oauth2.sdk.http.HTTPRequest;

public class TestSSLUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(TestSSLUtils.class);
	
    public static void disableSSLVerification() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { LOGGER.debug("Do nothing. Only for test."); }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { LOGGER.debug("Do nothing. Only for test."); }
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        
        // Nimbus global configuration
        HTTPRequest.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HTTPRequest.setDefaultHostnameVerifier((hostname, session) -> true);
    }

}
