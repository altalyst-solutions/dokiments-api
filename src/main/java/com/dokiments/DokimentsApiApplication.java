package com.dokiments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

/**
 * Entry point of the Dokiments API.
 *
 * <p>Each direct sub-package is an application module (bounded context) verified by Spring Modulith. {@code shared} is
 * the shared kernel every module may use; see its {@code package-info.java}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic(sharedModules = "shared")
public class DokimentsApiApplication {

    static void main(String[] args) {
        SpringApplication.run(DokimentsApiApplication.class, args);
    }
}
