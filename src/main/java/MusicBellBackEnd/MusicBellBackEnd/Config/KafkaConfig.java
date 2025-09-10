package MusicBellBackEnd.MusicBellBackEnd.Config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.topics.es-sending}")
    private String esSendingTopicName;
    
    @Value("${spring.kafka.topics.es-retry}")
    private String esRetryTopicName;
    
    @Value("${spring.kafka.topics.es-dlq}")
    private String esDlqTopicName;

    /**
     * 📦 ES 연동 토픽 - 검색 색인용 메시지 처리
     */
    @Bean
    public NewTopic elasticSearchSendingTopic() {
        return TopicBuilder.name(esSendingTopicName)
                .partitions(2)
                .replicas(1)
                .build();
    }
    
    /**
     * 🔄 ES 재시도 토픽 - 실패한 메시지 재처리용
     */
    @Bean
    public NewTopic elasticSearchRetryTopic() {
        return TopicBuilder.name(esRetryTopicName)
                .partitions(1)
                .replicas(1)
                .build();
    }
    
    /**
     * ☠️ ES DLQ 토픽 - 최종 실패 메시지 저장용
     */
    @Bean
    public NewTopic elasticSearchDlqTopic() {
        return TopicBuilder.name(esDlqTopicName)
                .partitions(1)
                .replicas(1)
                .build();
    }
}