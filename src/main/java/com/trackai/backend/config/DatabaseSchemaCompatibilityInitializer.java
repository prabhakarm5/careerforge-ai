package com.trackai.backend.config;

import com.trackai.backend.enums.FeatureType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Keeps database enum check constraints compatible with Java enum additions.
 * Hibernate's schema update creates these checks, but does not expand an
 * existing PostgreSQL check when a new enum value is introduced.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DatabaseSchemaCompatibilityInitializer implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "wallet_transactions_feature_type_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!isPostgreSql()) {
            return;
        }

        String definition = jdbcTemplate.query(
                """
                SELECT pg_get_constraintdef(c.oid)
                FROM pg_constraint c
                WHERE c.conname = ?
                  AND c.conrelid = to_regclass('wallet_transactions')
                """,
                preparedStatement -> preparedStatement.setString(1, CONSTRAINT_NAME),
                resultSet -> resultSet.next() ? resultSet.getString(1) : null
        );

        if (definition != null && definition.toUpperCase(Locale.ROOT).contains("'INTERVIEW'")) {
            return;
        }

        String allowedValues = Arrays.stream(FeatureType.values())
                .map(value -> "'" + value.name() + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("ALTER TABLE wallet_transactions DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
        jdbcTemplate.execute("ALTER TABLE wallet_transactions ADD CONSTRAINT " + CONSTRAINT_NAME
                + " CHECK (feature_type IN (" + allowedValues + "))");

        log.info("Updated {} to support feature types: {}", CONSTRAINT_NAME, allowedValues);
    }

    private boolean isPostgreSql() throws Exception {
        if (jdbcTemplate.getDataSource() == null) {
            return false;
        }
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName()
                    .toLowerCase(Locale.ROOT)
                    .contains("postgresql");
        }
    }
}
