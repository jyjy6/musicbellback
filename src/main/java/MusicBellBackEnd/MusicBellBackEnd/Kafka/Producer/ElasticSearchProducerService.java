package MusicBellBackEnd.MusicBellBackEnd.Kafka.Producer;


import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.ElasticSearchEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticSearchProducerService {
    private final KafkaTemplate<String, ElasticSearchEvent> kafkaTemplate;
    private static final Logger log = LoggerFactory.getLogger(ElasticSearchProducerService.class);
    @Value("${spring.kafka.topics.es-sending}")
    private String esTopic;


    public void sendSyncEvent(Long artstId) {
        sendEvent(new ElasticSearchEvent(artstId, "sync"));
    }

    public void sendDeleteEvent(Long artstId) {
        sendEvent(new ElasticSearchEvent(artstId, "delete"));
    }

    private void sendEvent(ElasticSearchEvent event) {
        try {
            kafkaTemplate.send(esTopic, event);
            log.info("ElasticSearchEvent 전송: {}", event);
        } catch (Exception e) {
            log.error("ElasticSearchEvent 전송 실패", e);
            throw new GlobalException(
                    "ElasticSearch 이벤트 전송 실패",
                    "KAFKA_SEND_ERROR",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
