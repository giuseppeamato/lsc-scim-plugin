package it.pz8.lsc.plugins.connectors.scim.rs;

public final class ClientBuilderCustomizerFactory {

	private static ClientBuilderCustomizer clientBuilderCustomizer;
	
	private ClientBuilderCustomizerFactory() {
	}
	
    static {
        String customizerClass = System.getProperty(
                "clientBuilderCustomizer.class",
                DefaultClientBuilderCustomizer.class.getName()
        );

        try {
        	clientBuilderCustomizer = (ClientBuilderCustomizer) Class
                    .forName(customizerClass)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate ClientBuilderCustomizer", e);
        }
    }
	
    public static ClientBuilderCustomizer create() {
        return clientBuilderCustomizer;
    }

}
