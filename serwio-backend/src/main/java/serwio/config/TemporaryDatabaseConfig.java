package serwio.config;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class TemporaryDatabaseConfig implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("app.database.temporary", Boolean.class, false)) {
            return;
        }

        Map<String, Object> properties = Map.of(
                "spring.datasource.url", environment.getRequiredProperty("app.database.temporary-url"),
                "spring.datasource.driver-class-name", "org.h2.Driver",
                "spring.datasource.username", "sa",
                "spring.datasource.password", "",
                "spring.jpa.hibernate.ddl-auto", "create-drop",
                "spring.docker.compose.enabled", "false"
        );
        environment.getPropertySources().addFirst(new MapPropertySource("temporaryDatabase", properties));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
