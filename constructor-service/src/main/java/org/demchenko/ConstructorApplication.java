package org.demchenko;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "org\\.demchenko\\.telegram\\..*"
))
public class ConstructorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConstructorApplication.class, args);
    }
}
