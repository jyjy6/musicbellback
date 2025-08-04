package MusicBellBackEnd.MusicBellBackEnd.Kafka.Event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ElasticSearchEvent {
    private Long musicId;
    private String action; // "sync" or "delete"
}
