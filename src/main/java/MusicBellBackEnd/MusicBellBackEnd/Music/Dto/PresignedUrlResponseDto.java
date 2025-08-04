package MusicBellBackEnd.MusicBellBackEnd.Music.Dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PresignedUrlResponseDto {
    
    private String presignedUrl; // S3 업로드용 presigned URL
    private String usableUrl; // 업로드 후 접근할 수 있는 음악 파일 URL
}