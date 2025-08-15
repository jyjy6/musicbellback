package MusicBellBackEnd.MusicBellBackEnd.Artist.Dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArtistSearchDto {
    
    private String name;
    private String genre;
    private String country;
    private Boolean isVerified;
    
    // 정렬 옵션
    private String sortBy = "createdAt"; // createdAt, followerCount, totalPlayCount, name
    private String sortOrder = "desc"; // asc, desc
    
    // 페이징
    private Integer page = 0;
    private Integer size = 20;
}
