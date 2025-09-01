package MusicBellBackEnd.MusicBellBackEnd.Kafka.Consumer;


import MusicBellBackEnd.MusicBellBackEnd.Artist.ElasticSearch.ArtistSyncService;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.ElasticSearchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticSearchConsumerService {
    private final ArtistSyncService artistSyncService;

    @KafkaListener(
            topics = "${spring.kafka.topics.es-sending}",
            groupId = "${spring.kafka.es.consumer.group-id}"
    )
    public void handleElasticSearchEvent(
            @Payload ElasticSearchEvent event,
            Acknowledgment acknowledgment) {

        try {
            if ("sync".equals(event.getAction())) {
                artistSyncService.syncSingleArtist(event.getArtistId());
                acknowledgment.acknowledge();
                log.info("ElasticSearch 동기화 완료: {}", event.getArtistId());
            } else if ("delete".equals(event.getAction())) {
//                imageSyncService.deleteFromIndex(event.getMusicId());
                artistSyncService.deleteFromIndex(event.getArtistId());
                acknowledgment.acknowledge();
                log.info("ElasticSearch 인덱스 삭제 완료: {}", event.getArtistId());
            } else {
                throw new GlobalException("알 수 없는 ElasticSearch 작업 타입", "ELASTICSEARCH_UNKNOWN_ACTION");
            }
        } catch (Exception e) {
            log.error("ElasticSearchConsumer 처리 실패", e);
            throw new GlobalException("ElasticSearchConsumer 처리 실패", "ELASTICSEARCH_CONSUMER_ERROR");
        }
    }
}
