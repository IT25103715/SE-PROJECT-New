package com.cinemahub.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Only active with the "https" profile (see application-https.properties).
 *
 * With {@code server.ssl.enabled=true} Spring Boot serves ONLY HTTPS on {@code server.port}. This adds a
 * second, plain-HTTP connector so the usual {@code http://localhost:8080} keeps working next to
 * {@code https://<pc-ip>:8443}, which is what a phone uses for camera access.
 */
@Configuration
@Profile("https")
public class DevHttpsConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> plainHttpConnector(
            @Value("${cinemahub.http.port:8080}") int httpPort) {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(httpPort);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
