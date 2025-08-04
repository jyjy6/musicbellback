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
}