package MusicBellBackEnd.MusicBellBackEnd.Lyrics;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "lyrics_lines")
public class LyricsLineEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lyrics_id", nullable = false)
    private LyricsEntity lyrics;
    
    @Column(nullable = false)
    private Integer startTime; // 시작 시간 (밀리초)
    
    @Column(nullable = false)
    private Integer endTime; // 종료 시간 (밀리초)
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String text; // 가사 텍스트
    
    @Column(nullable = false)
    private Integer lineOrder; // 라인 순서
    
    @Column(length = 50)
    private String type = "VOCAL"; // 라인 타입 (VOCAL, INSTRUMENTAL, CHORUS, etc.)
}