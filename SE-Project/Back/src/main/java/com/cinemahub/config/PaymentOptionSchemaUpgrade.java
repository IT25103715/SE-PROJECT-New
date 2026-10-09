package com.cinemahub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * One-off column fixes for the existing MySQL "payment_options" table, needed by the
 * type-specific payment method fields (Apple Pay / PayPal / Bank Transfer).
 *
 * ddl-auto=update adds the new nullable columns by itself, but it never changes existing ones:
 * <ul>
 *   <li>{@code type} may have been created as a native MySQL ENUM limited to the original three
 *       values, which would reject APPLE_PAY / PAYPAL ("Data truncated for column 'type'") - same
 *       gotcha described next to preferred_enum_jdbc_type in application.properties. It is widened
 *       to a plain VARCHAR.</li>
 *   <li>{@code provider_details} was NOT NULL, but new Apple Pay / PayPal / Bank Transfer options
 *       no longer use it - it becomes nullable.</li>
 * </ul>
 * Both statements are safe to run on every start (they leave existing data untouched). Runs first,
 * before DataSeeder / PayPalOptionInitializer. Skipped on non-MySQL databases (e.g. the h2 profile,
 * whose tables are created fresh with the right column types anyway).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentOptionSchemaUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentOptionSchemaUpgrade.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public PaymentOptionSchemaUpgrade(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("mysql")) {
                return;
            }
        } catch (Exception ex) {
            log.warn("Could not detect the database type - skipping payment_options column upgrade");
            return;
        }
        runQuietly("ALTER TABLE payment_options MODIFY COLUMN type VARCHAR(30) NOT NULL");
        runQuietly("ALTER TABLE payment_options MODIFY COLUMN provider_details VARCHAR(500) NULL");
        // Update 11 (Combo promotions): promotions.discount_type may be a native ENUM of only
        // PERCENTAGE/FIXED_AMOUNT, which would reject COMBO - widen it the same way.
        runQuietly("ALTER TABLE promotions MODIFY COLUMN discount_type VARCHAR(30) NOT NULL");
        // Payment timeout: bookings.status may be a native ENUM of PENDING/CONFIRMED/CANCELLED,
        // which would reject EXPIRED - widen it the same way.
        runQuietly("ALTER TABLE bookings MODIFY COLUMN status VARCHAR(30) NOT NULL");
    }

    private void runQuietly(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("payment_options column upgrade skipped ({}): {}", sql, ex.getMessage());
        }
    }
}
