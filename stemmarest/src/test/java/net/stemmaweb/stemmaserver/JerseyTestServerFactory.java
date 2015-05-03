package net.stemmaweb.stemmaserver;

import java.util.Collection;
import java.util.HashSet;

import javax.ws.rs.client.Client;




public class JerseyTestServerFactory {
	private final Collection<Object> resources = new HashSet<>();
	private Integer port;

	public static JerseyTestServerFactory newJerseyTestServer() {
		return new JerseyTestServerFactory();
	}

	/**
	 * 
	 * @param resource
	 * @return this JerseyTestServerFactory
	 */
	public JerseyTestServerFactory addResource(Object resource) {
		resources.add(resource);
		return this;
	}

	/**
	 * 
	 * @param port as int
	 * @return this as JerseyTestServerFactory
	 */
	public JerseyTestServerFactory setPort(int port) {
		this.port = port;
		return this;
	}

	/**
	 * 
	 * @return JerseyTest
	 */
	public JerseyTest create() {
		return new JerseyTest() {
			@Override
			protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
				return new GrizzlyTestContainerFactory();
			}

			@Override
			protected int getPort(int defaultPort) {
				if (port == null) {
					return super.getPort(defaultPort);
				} else {
					return port;
				}
			}

			@Override
			protected ClientFactory getClientFactory() {
				return new ClientFactory() {
					@Override
					public Client create(ClientConfig clientConfig) {
						Client client = Client.create(clientConfig);
						client.addFilter(new LoggingFilter());
						return client;
					}
				};
			}

			/**
			 * Populate the App with the configured Resources
			 */
			@Override
			protected AppDescriptor configure() {
				DefaultResourceConfig resourceConfig = new DefaultResourceConfig();
				resourceConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, true);
				for (Object resource : resources) {
					resourceConfig.getSingletons().add(resource);
				}
				ClientConfig clientConfig = new DefaultClientConfig();
				clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, true);
				return new LowLevelAppDescriptor.Builder(resourceConfig).clientConfig(clientConfig).build();
			}
		};
	}
}
