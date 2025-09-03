package MusicBellBackEnd.MusicBellBackEnd.Lyrics;

import MusicBellBackEnd.MusicBellBackEnd.Music.MusicEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "lyrics")
public class LyricsEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "music_id", nullable = false)
    private MusicEntity music;
    
    // LyricsLineEntity와의 연관관계 추가
    @OneToMany(mappedBy = "lyrics", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LyricsLineEntity> lines = new ArrayList<>();
    
    @Column(columnDefinition = "TEXT")
    private String fullLyrics; // 전체 가사 (일반 텍스트)
    
    @Column(columnDefinition = "TEXT")
    private String syncedLyrics; // 동기화된 가사 (LRC 형식 또는 JSON)
    
    @Column(length = 10)
    private String language = "KO"; // 가사 언어 (KO, EN, JP, etc.)
    
    @Column(length = 50)
    private String source = "AI_GENERATED"; // 가사 출처 (AI_GENERATED, MANUAL, IMPORTED)
    
    @Column(name = "generation_type", length = 50)
    private String generationType = "SPEECH_TO_TEXT"; // 생성 방식 (SPEECH_TO_TEXT, MANUAL_INPUT, IMPORTED)
    
    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean isVerified = false; // 검증된 가사 여부
    
    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean isActive = true; // 활성화 여부
    
    @Column(length = 100)
    private String createdBy; // 생성자 (AI 또는 사용자명)
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}