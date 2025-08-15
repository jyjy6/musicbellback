package MusicBellBackEnd.MusicBellBackEnd.Artist.Dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArtistResponseDto {
    
    private Long id;
    private String name;
    private String description;
    private String profileImageUrl;
    private String genre;
    private String country;
    private String agency;
    private Boolean isVerified;
    private Boolean isActive;
    private Long followerCount;
    private Long totalPlayCount;
    private Long totalLikeCount;
    private String spotifyId;
    private String appleMusicId;
    private String youtubeChannelId;
    private String officialWebsite;
    private String instagramHandle;
    private String twitterHandle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
