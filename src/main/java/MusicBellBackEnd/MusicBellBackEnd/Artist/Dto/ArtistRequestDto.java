package MusicBellBackEnd.MusicBellBackEnd.Artist.Dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArtistRequestDto {
    
    private String name;
    private String description;
    private String profileImageUrl;
    private String genre;
    private String country;
    private String agency;
    private String officialWebsite;
    private String instagramHandle;
    private String twitterHandle;
    private String spotifyId;
    private String appleMusicId;
    private String youtubeChannelId;
}
