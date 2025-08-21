package MusicBellBackEnd.MusicBellBackEnd.Config;


import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableRedisRepositories
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private Integer redisPort;


    @Bean
    public RedisConnectionFactory redisConnectionFactory() {

/**대용량 트래픽을 위한연결 풀 설정
 //        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
 //        poolConfig.setMaxTotal(20);         // 최대 연결 수
 //        poolConfig.setMaxIdle(10);          // 최대 유휴 연결 수
 //        poolConfig.setMinIdle(2);           // 최소 유휴 연결 수
 //        poolConfig.setTestOnBorrow(true);   // 연결 시 유효성 검사
 //        poolConfig.setTestOnReturn(true);   // 반환 시 유효성 검사
 //        poolConfig.setTestWhileIdle(true);  // 유휴 상태에서 유효성 검사
 //
 //        // Lettuce 클라이언트 설정
 //        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
 //                .poolConfig(poolConfig)
 //                .commandTimeout(Duration.ofSeconds(5))  // 명령 타임아웃 5초
 //                .build();
 LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379, clientConfig);
 **/

        // 기본 설정으로 간단하게 구성
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisHost, redisPort);
        factory.setValidateConnection(true);
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());

        // 직렬화 설정
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        // 트랜잭션 지원 활성화
        template.setEnableTransactionSupport(true);

        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)) // TTL: 30분
                .disableCachingNullValues()       // null 값 캐싱 비활성화
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .transactionAware()  // 트랜잭션 인식
                .build();
    }
}