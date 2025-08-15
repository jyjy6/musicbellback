package MusicBellBackEnd.MusicBellBackEnd.Artist;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "artists")
public class ArtistEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 200, unique = true)
    private String name; // 아티스트명 (중복 불가)
    
    @Column(length = 500)
    private String description; // 아티스트 소개
    
    @Column(columnDefinition = "TEXT")
    private String profileImageUrl; // 프로필 이미지 URL
    
    @Column(length = 100)
    private String genre; // 주 장르
    
    @Column(length = 200)
    private String country; // 국가/출신지
    
    @Column(length = 100)
    private String agency; // 소속사/레이블
    
    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean isVerified = false; // 인증된 아티스트 여부
    
    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean isActive = true; // 활동 상태
    
    @Column(columnDefinition = "integer default 0")
    private Long followerCount = 0L; // 팔로워 수
    
    @Column(columnDefinition = "integer default 0")
    private Long totalPlayCount = 0L; // 총 재생수
    
    @Column(columnDefinition = "integer default 0")
    private Long totalLikeCount = 0L; // 총 좋아요 수
    
    @Column(length = 100)
    private String spotifyId; // Spotify 아티스트 ID (외부 연동용)
    
    @Column(length = 100)
    private String appleMusicId; // Apple Music 아티스트 ID (외부 연동용)
    
    @Column(length = 100)
    private String youtubeChannelId; // YouTube 채널 ID (외부 연동용)
    
    @Column(length = 200)
    private String officialWebsite; // 공식 웹사이트
    
    @Column(length = 100)
    private String instagramHandle; // 인스타그램 핸들 (@username)
    
    @Column(length = 100)
    private String twitterHandle; // 트위터 핸들 (@username)
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
}
