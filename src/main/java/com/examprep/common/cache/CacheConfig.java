package com.examprep.common.cache;

import com.examprep.catalog.dto.CatalogTreeDto;
import com.examprep.catalog.dto.ExamListDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis cache manager.
 *
 * <ul>
 *   <li><b>Typed JSON per cache.</b> Each cache has a Jackson serializer bound to one
 *       concrete DTO type. That avoids JDK serialization and polymorphic
 *       {@code @class} metadata, and makes the cached payloads readable in redis-cli.</li>
 *   <li><b>No implicit caches.</b> An unregistered cache name fails fast instead of
 *       silently falling back to a serializer that cannot handle records.</li>
 *   <li><b>SCAN-based clear.</b> {@code allEntries} eviction never issues a blocking {@code KEYS}.</li>
 *   <li><b>Fail-open.</b> {@link LoggingCacheErrorHandler} turns Redis errors into cache
 *       misses, so a Redis outage degrades to DB reads instead of 500s.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("cache:")
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));

        Map<String, RedisCacheConfiguration> caches = Map.of(
                CacheNames.CATALOG_EXAMS, typed(base, objectMapper, ExamListDto.class).entryTtl(Duration.ofHours(6)),
                CacheNames.CATALOG_TREE, typed(base, objectMapper, CatalogTreeDto.class).entryTtl(Duration.ofHours(6)));

        return RedisCacheManager.builder(
                        RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(500)))
                .cacheDefaults(base)
                .withInitialCacheConfigurations(caches)
                .disableCreateOnMissingCache()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    private static RedisCacheConfiguration typed(RedisCacheConfiguration base, ObjectMapper mapper, Class<?> type) {
        return base.serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, type)));
    }
}
