package org.koaks.codereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeAsReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAsReviewApplication.class, args);
    }

}
