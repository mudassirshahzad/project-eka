package com.mudassir.eka.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.mudassir.eka.infrastructure.persistence.postgres.repository"
)
@EntityScan(
        basePackages = "com.mudassir.eka.infrastructure.persistence.postgres.entity"
)
public class DatabaseConfig {
}
