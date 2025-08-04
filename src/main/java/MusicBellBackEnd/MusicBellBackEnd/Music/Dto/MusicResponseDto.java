package MusicBellBackEnd.MusicBellBackEnd.Music.Dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusicResponseDto {
    
    private Long id;
    private String title;
    private String artist;
    private String album;
    private String genre;
    private LocalDate releaseDate;
    private Integer duration; // 재생시간 (초 단위)
    private String musicUrl; // S3에 저장된 음악 파일 URL
    private String albumImageUrl; // 앨범 커버 이미지 URL
    private String uploaderName;
    private Long playCount; // 재생횟수
    private Long likeCount; // 좋아요 수
    private Boolean isPublic; // 공개 여부
    private Long fileSize; // 파일 크기 (바이트)
    private String fileType; // 파일 타입 (mp3, wav, flac 등)
    private String musicGrade; // 음악 등급 (GENERAL, EXPLICIT 등)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}