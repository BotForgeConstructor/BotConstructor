package org.demchenko;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@SpringBootApplication
public class ConstructorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConstructorApplication.class, args);
    }
}
