package serwio.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class AccountEmailConfig {
    @Bean
    @DependsOn("entityManagerFactory")
    InitializingBean accountEmailIndex(DataSource dataSource) {
        return () -> {
            var jdbc = new JdbcTemplate(dataSource);
            try (var connection = dataSource.getConnection()) {
                if ("H2".equals(connection.getMetaData().getDatabaseProductName())) {
                    jdbc.execute("ALTER TABLE user_account ADD COLUMN IF NOT EXISTS email_normalized "
                            + "varchar(254) GENERATED ALWAYS AS (lower(trim(email)))");
                    jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_user_account_email_normalized "
                            + "ON user_account(email_normalized)");
                } else {
                    jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_user_account_email_normalized "
                            + "ON user_account(lower(trim(email)))");
                }
            }
        };
    }
}
