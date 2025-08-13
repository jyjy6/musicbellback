package MusicBellBackEnd.MusicBellBackEnd.Music;

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
    
    @Column(nullable = false, length = 200)
    private String artist;
    
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
    

}
