package MusicBellBackEnd.MusicBellBackEnd.Redis;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaylistItem {
    @JsonProperty("id")
    private Long musicId;
    private String musicTitle;
    private String musicUrl;
    private LocalDateTime addedAt;
}