package com.examprep.common.redis;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tiny cluster-wide signal bus on Redis pub/sub (works on any Redis version, no Streams).
 * Used to drop in-memory caches on every instance at once, e.g. when an admin edits a role.
 *
 * <p>Delivery is best-effort (pub/sub is fire-and-forget), so every cache that relies on it
 * also has a short TTL. Local handlers run synchronously on {@link #publish}, so the
 * instance that made the change never serves stale data.
 */
@Slf4j
@Component
public class ClusterEvents implements MessageListener {

    public static final String CHANNEL = "examprep:cluster-events";

    private final StringRedisTemplate redis;
    private final Map<String, List<Runnable>> handlers = new ConcurrentHashMap<>();
    private final RedisMessageListenerContainer container;

    public ClusterEvents(StringRedisTemplate redis, RedisConnectionFactory connectionFactory) {
        this.redis = redis;
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(this, new ChannelTopic(CHANNEL));
        container.setErrorHandler(e -> log.warn("Cluster event listener error: {}", e.getMessage()));
        container.afterPropertiesSet();
        container.start();
    }

    @PreDestroy
    void stop() throws Exception {
        container.destroy();
    }

    /** Registers a handler for a topic (e.g. "rbac"). */
    public void on(String topic, Runnable handler) {
        handlers.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Runs local handlers now and tells the other instances. */
    public void publish(String topic) {
        runLocal(topic);
        try {
            redis.convertAndSend(CHANNEL, topic);
        } catch (DataAccessException e) {
            log.warn("Could not broadcast cluster event '{}': {} (other instances refresh on TTL)", topic,
                    e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        runLocal(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    private void runLocal(String topic) {
        for (Runnable handler : handlers.getOrDefault(topic, List.of())) {
            try {
                handler.run();
            } catch (RuntimeException e) {
                log.warn("Cluster event handler for '{}' failed", topic, e);
            }
        }
    }
}
