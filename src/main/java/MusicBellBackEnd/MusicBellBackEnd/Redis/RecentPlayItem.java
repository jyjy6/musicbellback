package MusicBellBackEnd.MusicBellBackEnd.Redis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecentPlayItem {
    
    private Long id;
    private String title;
    private String albumImageUrl;
    private String artist;
    private Integer duration;
}
