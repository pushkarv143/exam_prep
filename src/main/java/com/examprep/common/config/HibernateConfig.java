package com.examprep.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes Hibernate use Spring's {@link ObjectMapper} for {@code @JdbcTypeCode(SqlTypes.JSON)}
 * columns. JSONB then has the same settings as the REST layer (JavaTimeModule,
 * NON_NULL, lenient unknown fields). Without this, Hibernate builds its own
 * ObjectMapper with different defaults.
 */
@Configuration
public class HibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer jsonFormatMapperCustomizer(ObjectMapper objectMapper) {
        return properties -> properties.put(AvailableSettings.JSON_FORMAT_MAPPER,
                new JacksonJsonFormatMapper(objectMapper));
    }
}
