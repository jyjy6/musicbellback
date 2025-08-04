package MusicBellBackEnd.MusicBellBackEnd.Music.Dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusicUploadRequestDto {
    
    private List<MusicRequestDto> musicList;
}