package net.stemmaweb.stemmaserver;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.grizzly.GrizzlyTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
/*
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.LoggingFilter;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.LowLevelAppDescriptor;
import com.sun.jersey.test.framework.spi.client.ClientFactory;
import com.sun.jersey.test.framework.spi.container.TestContainerException;
import com.sun.jersey.test.framework.spi.container.TestContainerFactory;
import com.sun.jersey.test.framework.spi.container.grizzly2.GrizzlyTestContainerFactory;
*/
/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class JerseyTestServerFactory {
    private final Set<Class<?>> resources = new HashSet<Class<?>>();
    private Integer port;

    public static JerseyTestServerFactory newJerseyTestServer() {
        return new JerseyTestServerFactory();
    }

    /**
     *
     * @param resource -
     * @return this JerseyTestServerFactory
     */
    public JerseyTestServerFactory addResource(Class<?> resource) {
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
/*
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
                return clientConfig -> {
                    Client client = Client.create(clientConfig);
                    client.addFilter(new LoggingFilter());
                    return client;
                };
            }
*/
            /**
             * Populate the App with the configured Resources
             */
            

            @Override
            protected Application configure() {

                ResourceConfig resourceConfig = new ResourceConfig();
                resourceConfig.register(MultiPartFeature.class);
//                resourceConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, true);
                resourceConfig.registerClasses(resources);
                

                //ClientConfig clientConfig = new DefaultClientConfig();
                //clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, true);
                return resourceConfig;
            }
            @Override
            protected void configureClient(ClientConfig config) {
                config.register(MultiPartFeature.class);
            }         
        };
    }
}
