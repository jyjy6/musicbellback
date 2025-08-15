package MusicBellBackEnd.MusicBellBackEnd.Music;

import MusicBellBackEnd.MusicBellBackEnd.Artist.ArtistEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusicEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 500)
    private String title;
    
    // 기존 데이터 보존 (마이그레이션 완료 후 제거 예정)
    @Column(length = 200)
    private String artist; // 기존 필드명 그대로 유지하여 데이터 보존
    
    // 새로운 아티스트 관계 (NULL 허용으로 점진적 전환)
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "artist_id")
    private ArtistEntity artistEntity; // 기존 필드와 구분하기 위해 다른 이름 사용
    
    @Column(length = 200)
    private String album;
    
    @Column(length = 100)
    private String genre;
    
    @Column
    private LocalDate releaseDate;
    
    @Column
    private Integer duration; // 재생시간 (초 단위)
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String musicUrl; // S3에 저장된 음악 파일 URL
    
    @Column(columnDefinition = "TEXT")
    private String albumImageUrl; // 앨범 커버 이미지 URL
    
    @Column(nullable = false, length = 100)
    private String uploaderName;

    private Long uploaderId;
    
    @Column(columnDefinition = "integer default 0")
    private Long playCount = 0L; // 재생횟수
    
    @Column(columnDefinition = "integer default 0")
    private Long likeCount = 0L; // 좋아요 수
    
    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean isPublic = true; // 공개 여부
    
    @Column
    private Long fileSize; // 파일 크기 (바이트)
    
    @Column(length = 100)
    private String fileType; // 파일 타입 (mp3, wav, flac 등)
    
    @Column(length = 50)
    private String musicGrade = "GENERAL"; // 음악 등급 (GENERAL, EXPLICIT 등)
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    // === 마이그레이션 관련 헬퍼 메소드 ===
    
    /**
     * 아티스트명을 반환 (새 관계 우선, 없으면 기존 필드)
     */
    public String getArtistDisplayName() {
        if (artistEntity != null) {
            return artistEntity.getName();
        }
        return artist; // 기존 String 필드
    }
    
    /**
     * 기존 String artist 필드를 ArtistEntity로 마이그레이션하기 위한 헬퍼
     */
    public void migrateToArtistEntity(ArtistEntity artistEntity) {
        this.artistEntity = artistEntity;
        // 마이그레이션 완료 후에는 기존 artist 필드를 null로 설정할 수 있음
        // this.artist = null; 
    }

}
