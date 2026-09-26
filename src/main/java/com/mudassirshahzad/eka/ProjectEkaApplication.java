package com.mudassirshahzad.eka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded deliberately. Authentication
 * in EKA is JWT-only — {@code SecurityConfig} registers no {@code httpBasic()} and no
 * {@code formLogin()}, and {@code JwtAuthenticationFilter} is the single authentication
 * mechanism — so Spring Boot's fallback {@code InMemoryUserDetailsManager} has nothing
 * that could ever authenticate against it.
 *
 * <p>Leaving it enabled was not a vulnerability (the account was unreachable, and nothing
 * in this application injects an {@code AuthenticationManager}), but it made every
 * production startup log:
 *
 * <pre>Using generated security password: 7d5e67a9-...</pre>
 *
 * which tells an operator reading the logs that a default account exists. Excluding the
 * auto-configuration removes an unused bean and a misleading line from production logs.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class ProjectEkaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectEkaApplication.class, args);
    }
}
