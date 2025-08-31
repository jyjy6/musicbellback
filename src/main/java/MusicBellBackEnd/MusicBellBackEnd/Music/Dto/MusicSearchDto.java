package MusicBellBackEnd.MusicBellBackEnd.Music.Dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusicSearchDto {
    
    private String title;
    private Long artist;
    private String album;
    private String genre;
    private String uploaderName;
    private String uploaderId;
    private Boolean isPublic;
    private String musicGrade;
    
    // 정렬 옵션
    private String sortBy = "createdAt"; // createdAt, playCount, likeCount, title, artist
    private String sortOrder = "desc"; // asc, desc
    
    // 페이징
    private Integer page = 0;
    private Integer size = 20;
}