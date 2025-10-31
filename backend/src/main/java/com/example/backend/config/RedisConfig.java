package com.example.backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration
 * 
 * Configures:
 * 1. Connection to Redis server
 * 2. Cache Manager with different TTLs per cache
 * 3. Serialization (how to store Java objects in Redis)
 * 
 * @EnableCaching enables Spring's caching annotations (@Cacheable, @CacheEvict, etc.)
 */
@Configuration
@EnableCaching  // ⭐ Activates @Cacheable, @CacheEvict, @CachePut annotations
@Slf4j
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Creates Redis connection factory using Lettuce client
     * Lettuce is async, thread-safe, and supports Redis Cluster
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("🔵 Initializing Redis connection to {}:{}", redisHost, redisPort);
        
        // Configure Redis connection
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        
        // Set password if provided
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        
        log.info("✅ Redis connection factory created");
        return factory;
    }

    /**
     * Custom ObjectMapper for Redis serialization ONLY
     * Supports:
     * - Java 8 Date/Time API (LocalDateTime, LocalDate, etc.)
     * - Polymorphic type information (to deserialize correctly)
     * 
     * NOTE: This ObjectMapper is ONLY for Redis internal use.
     * It should NOT affect REST API JSON serialization.
     */
    @Bean(name = "redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register Java 8 Date/Time module
        // Without this, LocalDateTime won't serialize/deserialize correctly
        mapper.registerModule(new JavaTimeModule());
        
        // Write dates as ISO-8601 strings instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Add type information to JSON for polymorphic deserialization
        // This prevents ClassCastException when retrieving objects from cache
        mapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        return mapper;
    }

    /**
     * RedisTemplate for direct Redis operations
     * (Not required if only using @Cacheable annotations)
     * 
     * Useful for:
     * - Manual cache operations
     * - Pub/Sub messaging
     * - Custom Redis commands
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper redisObjectMapper) {
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // String serializer for keys (human-readable in Redis)
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        
        // JSON serializer for values (supports complex objects)
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        
        // Configure serializers
        template.setKeySerializer(stringSerializer);           // Keys as strings
        template.setValueSerializer(jsonSerializer);           // Values as JSON
        template.setHashKeySerializer(stringSerializer);       // Hash keys as strings
        template.setHashValueSerializer(jsonSerializer);       // Hash values as JSON
        
        template.afterPropertiesSet();
        
        log.info("✅ RedisTemplate configured");
        return template;
    }

    /**
     * Cache Manager - Manages all application caches
     * 
     * Each cache can have different:
     * - TTL (Time To Live)
     * - Eviction policy
     * - Serialization strategy
     */
    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper redisObjectMapper) {
        
        log.info("🔵 Configuring Redis Cache Manager");
        
        // Default cache configuration (applies to all caches unless overridden)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))  // Default TTL: 1 hour
            .disableCachingNullValues()     // Don't cache null values
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper))
            );
        
        // Custom configurations for specific caches
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        
        // ⭐ Chat messages cache - 30 minutes
        // Reason: Users frequently return to conversations
        cacheConfigs.put("chatMessages", 
            defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // ⭐ Recent messages cache - 10 minutes
        // Reason: Changes frequently as new messages arrive
        cacheConfigs.put("recentMessages", 
            defaultConfig.entryTtl(Duration.ofMinutes(10)));
        
        // ⭐ Chat details cache - 1 hour
        // Reason: Metadata changes infrequently
        cacheConfigs.put("chatDetails", 
            defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // ⭐ User chats list cache - 15 minutes
        // Reason: List updates when creating/deleting chats
        cacheConfigs.put("userChats", 
            defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Build the cache manager
        CacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .transactionAware()  // Support Spring transactions
            .build();
        
        log.info("✅ Redis Cache Manager configured with {} cache types", cacheConfigs.size());
        return cacheManager;
    }
}