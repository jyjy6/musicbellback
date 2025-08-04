package MusicBellBackEnd.MusicBellBackEnd.Music.Dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusicUpdateDto {
    
    private String title;
    private String artist;
    private String album;
    private String genre;
    private LocalDate releaseDate;
    private Integer duration; // 재생시간 (초 단위)
    private String albumImageUrl; // 앨범 커버 이미지 URL
    private Boolean isPublic; // 공개 여부
    private String musicGrade; // 음악 등급 (GENERAL, EXPLICIT 등)
}