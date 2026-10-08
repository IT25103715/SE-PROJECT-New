package com.cinemahub;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class CinemaHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(CinemaHubApplication.class, args);
    }

    /**
     * Prints the local site URL(s) once the server is actually accepting
     * requests and automatically opens one in Microsoft Edge.
     * The URLs come from Tomcat's real connectors (actual scheme + port), not from
     * config - with the dev "https" profile the main port is HTTPS-only 8443 plus a
     * plain-HTTP 8080 connector (DevHttpsConfig), and "http://localhost:8443" would
     * just fail with "This combination of host and port requires TLS".
     * Set cinemahub.open-browser=false (or env CINEMAHUB_OPEN_BROWSER=false) to skip opening a
     * browser, e.g. for a second test copy of the app or on a server.
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> siteUrlPrinter() {
        return event -> {
            List<String> urls = new ArrayList<>();
            if (event.getApplicationContext() instanceof ServletWebServerApplicationContext webContext
                    && webContext.getWebServer() instanceof TomcatWebServer tomcat) {
                for (Connector connector : tomcat.getTomcat().getService().findConnectors()) {
                    urls.add(connector.getScheme() + "://localhost:" + connector.getLocalPort());
                }
            }
            if (urls.isEmpty()) {
                urls.add("http://localhost:" + event.getApplicationContext().getEnvironment()
                        .getProperty("local.server.port", "8080"));
            }
            System.out.println("=======================================================");
            for (String url : urls) {
                System.out.println(" CinemaHub is running at: " + url);
            }
            System.out.println("=======================================================");
            if (!event.getApplicationContext().getEnvironment()
                    .getProperty("cinemahub.open-browser", Boolean.class, true)) {
                return;
            }
            // Plain HTTP opens without the self-signed-certificate warning, so prefer it.
            openBrowser(urls.stream().filter(u -> u.startsWith("http:")).findFirst().orElse(urls.get(0)));
        };
    }

    /**
     * Specifically launches Microsoft Edge, with default browser fallback.
     */
    private void openBrowser(String url) {
        try {
            // Windows CLI command to open Microsoft Edge explicitly
            Runtime.getRuntime().exec("cmd /c start msedge " + url);
        } catch (Exception ex) {
            // Fallback to system default browser if Edge path is not found
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                } else {
                    Runtime.getRuntime().exec("cmd /c start " + url);
                }
            } catch (Exception e) {
                System.out.println(" (couldn't auto-open a browser: " + e.getMessage() + ")");
            }
        }
    }
}