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

    public static final File cachedVideoFile;

    static {
        try (InputStream inputStream = ConstructorApplication.class.getClassLoader()
                .getResourceAsStream("assets/videos/video.mp4")) {
            if (inputStream == null) throw new RuntimeException("Video not found in resources!");

            File tmpFile = File.createTempFile("video", ".mp4");
            Files.copy(inputStream, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            cachedVideoFile = tmpFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to cache video file", e);
        }
    }
}
