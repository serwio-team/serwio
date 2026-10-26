package serwio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import serwio.account.repository.UserAccountRepository;
import serwio.security.AccountStatusFilter;
import serwio.security.LoginHandlers;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginHandlers loginHandlers,
                                            UserAccountRepository accounts) throws Exception {
        return http
                .addFilterBefore(new AccountStatusFilter(accounts), AuthorizationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/csrf").permitAll()
                        .requestMatchers("/api", "/api/users/me", "/api/users/me/password").authenticated()
                        .requestMatchers("/api/owners/me").hasRole("ASSET_OWNER")
                        .requestMatchers("/api/technicians/me").hasRole("TECHNICIAN")
                        .requestMatchers("/api/companies/me", "/api/companies/me/summary", "/api/branches",
                                "/api/branches/**", "/api/technicians", "/api/technicians/**")
                                .hasRole("SERVICE_COMPANY")
                        .requestMatchers("/api/locations", "/api/locations/**",
                                "/api/service-objects", "/api/service-objects/**").hasRole("ASSET_OWNER")
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form
                        .defaultSuccessUrl("/api", true)
                        .successHandler(loginHandlers::onSuccess)
                        .failureHandler(loginHandlers::onFailure)
                        .permitAll())
                .logout(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .build();
    }
}
