package com.genai.gitgpt.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SchemaCompat implements ApplicationRunner {

    private final DataSource dataSource;
    private final String schema;

    public SchemaCompat(
            DataSource dataSource,
            @Value("${spring.jpa.properties.hibernate.default_schema}") String schema
    ) {
        this.dataSource = dataSource;
        this.schema = schema;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (schema == null || !schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("Refusing to patch schema with an unsafe name: " + schema);
        }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + schema + ".index_jobs ADD COLUMN IF NOT EXISTS cancel_requested boolean NOT NULL DEFAULT false");
            statement.execute("ALTER TABLE " + schema + ".index_jobs ADD COLUMN IF NOT EXISTS progress_step varchar(32)");
            statement.execute("ALTER TABLE " + schema + ".index_jobs ADD COLUMN IF NOT EXISTS progress_percent integer");
            statement.execute("ALTER TABLE " + schema + ".repos ADD COLUMN IF NOT EXISTS star_count integer");
            statement.execute("ALTER TABLE " + schema + ".repos ADD COLUMN IF NOT EXISTS fork_count integer");
            statement.execute("ALTER TABLE " + schema + ".repos ADD COLUMN IF NOT EXISTS github_pushed_at timestamp");
            statement.execute("ALTER TABLE " + schema + ".repos ADD COLUMN IF NOT EXISTS index_embedding_model varchar(64)");
            statement.execute("ALTER TABLE " + schema + ".users ADD COLUMN IF NOT EXISTS gemini_api_key text");
            statement.execute("ALTER TABLE " + schema + ".users ADD COLUMN IF NOT EXISTS chat_model varchar(64)");
            statement.execute("ALTER TABLE " + schema + ".users ADD COLUMN IF NOT EXISTS embedding_model varchar(64)");
            log.info("Ensured index progress and Gemini settings columns exist");
        } catch (Exception ex) {
            throw new IllegalStateException("Could not add missing index_jobs columns", ex);
        }
    }
}
