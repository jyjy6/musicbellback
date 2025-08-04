package MusicBellBackEnd.MusicBellBackEnd.Music.Dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusicStatsDto {
    
    private Long id;
    private String title;
    private String artist;
    private String albumImageUrl;
    private Long playCount;
    private Long likeCount;
}