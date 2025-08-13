package MusicBellBackEnd.MusicBellBackEnd.Music;

import MusicBellBackEnd.MusicBellBackEnd.Auth.CustomUserDetails;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Music.Dto.MusicRequestDto;
import MusicBellBackEnd.MusicBellBackEnd.Music.Dto.MusicResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MusicServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private MusicRepository musicRepository;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private Authentication authentication;

    @Mock
    private CustomUserDetails customUserDetails;

    @InjectMocks
    private MusicService musicService;

    private MusicRequestDto sampleDto1;
    private MusicRequestDto sampleDto2;

    @BeforeEach
    void setUp() {
        // CustomUserDetails 모킹 설정
        when(customUserDetails.getId()).thenReturn(123L);
        when(authentication.getPrincipal()).thenReturn(customUserDetails);

        sampleDto1 = MusicRequestDto.builder()
                .title("Song A")
                .artist("Artist A")
                .album("Album A")
                .genre("POP")
                .releaseDate(LocalDate.of(2024, 1, 1))
                .duration(180)
                .musicUrl("https://s3/test/song-a.mp3")
                .albumImageUrl("https://s3/test/album-a.jpg")
                .uploaderName("tester")
                // isPublic intentionally null to test default true
                .fileSize(123456L)
                .fileType("mp3")
                // musicGrade intentionally null to test default "GENERAL"
                .build();

        sampleDto2 = MusicRequestDto.builder()
                .title("Song B")
                .artist("Artist B")
                .album("Album B")
                .genre("ROCK")
                .releaseDate(LocalDate.of(2023, 12, 31))
                .duration(200)
                .musicUrl("https://s3/test/song-b.mp3")
                .albumImageUrl("https://s3/test/album-b.jpg")
                .uploaderName("tester")
                .isPublic(Boolean.TRUE)
                .fileSize(654321L)
                .fileType("mp3")
                .musicGrade("EXPLICIT")
                .build();

    }
    

    @Test
    @DisplayName("uploadMusics: DTO 리스트를 저장하고 동일 크기의 응답 DTO 리스트를 반환한다")
    void uploadMusics_success() {
        // given
        AtomicLong idSeq = new AtomicLong(1);
        when(musicRepository.save(any(MusicEntity.class))).thenAnswer(invocation -> {
            MusicEntity toSave = invocation.getArgument(0);
            return MusicEntity.builder()
                    .id(idSeq.getAndIncrement())
                    .title(toSave.getTitle())
                    .artist(toSave.getArtist())
                    .album(toSave.getAlbum())
                    .genre(toSave.getGenre())
                    .releaseDate(toSave.getReleaseDate())
                    .duration(toSave.getDuration())
                    .musicUrl(toSave.getMusicUrl())
                    .albumImageUrl(toSave.getAlbumImageUrl())
                    .uploaderName(toSave.getUploaderName())
                    .uploaderId(toSave.getUploaderId()) // uploaderId 추가
                    .playCount(toSave.getPlayCount())
                    .likeCount(toSave.getLikeCount())
                    .isPublic(toSave.getIsPublic())
                    .fileSize(toSave.getFileSize())
                    .fileType(toSave.getFileType())
                    .musicGrade(toSave.getMusicGrade())
                    .build();
        });

        // when
        List<MusicResponseDto> responses = musicService.uploadMusics(List.of(sampleDto1, sampleDto2), authentication);

        // then
        assertThat(responses).hasSize(2);

        MusicResponseDto res1 = responses.get(0);
        MusicResponseDto res2 = responses.get(1);

        assertAll(
                () -> assertThat(res1.getId()).isNotNull(),
                () -> assertThat(res1.getTitle()).isEqualTo(sampleDto1.getTitle()),
                () -> assertThat(res1.getArtist()).isEqualTo(sampleDto1.getArtist()),
                () -> assertThat(res1.getAlbum()).isEqualTo(sampleDto1.getAlbum()),
                () -> assertThat(res1.getGenre()).isEqualTo(sampleDto1.getGenre()),
                () -> assertThat(res1.getReleaseDate()).isEqualTo(sampleDto1.getReleaseDate()),
                () -> assertThat(res1.getDuration()).isEqualTo(sampleDto1.getDuration()),
                () -> assertThat(res1.getMusicUrl()).isEqualTo(sampleDto1.getMusicUrl()),
                () -> assertThat(res1.getAlbumImageUrl()).isEqualTo(sampleDto1.getAlbumImageUrl()),
                () -> assertThat(res1.getUploaderName()).isEqualTo(sampleDto1.getUploaderName()),
                () -> assertThat(res1.getIsPublic()).isTrue(),
                () -> assertThat(res1.getFileSize()).isEqualTo(sampleDto1.getFileSize()),
                () -> assertThat(res1.getFileType()).isEqualTo(sampleDto1.getFileType()),
                () -> assertThat(res1.getMusicGrade()).isEqualTo("GENERAL")
        );

        assertAll(
                () -> assertThat(res2.getId()).isNotNull(),
                () -> assertThat(res2.getTitle()).isEqualTo(sampleDto2.getTitle()),
                () -> assertThat(res2.getArtist()).isEqualTo(sampleDto2.getArtist()),
                () -> assertThat(res2.getAlbum()).isEqualTo(sampleDto2.getAlbum()),
                () -> assertThat(res2.getGenre()).isEqualTo(sampleDto2.getGenre()),
                () -> assertThat(res2.getReleaseDate()).isEqualTo(sampleDto2.getReleaseDate()),
                () -> assertThat(res2.getDuration()).isEqualTo(sampleDto2.getDuration()),
                () -> assertThat(res2.getMusicUrl()).isEqualTo(sampleDto2.getMusicUrl()),
                () -> assertThat(res2.getAlbumImageUrl()).isEqualTo(sampleDto2.getAlbumImageUrl()),
                () -> assertThat(res2.getUploaderName()).isEqualTo(sampleDto2.getUploaderName()),
                () -> assertThat(res2.getIsPublic()).isTrue(),
                () -> assertThat(res2.getFileSize()).isEqualTo(sampleDto2.getFileSize()),
                () -> assertThat(res2.getFileType()).isEqualTo(sampleDto2.getFileType()),
                () -> assertThat(res2.getMusicGrade()).isEqualTo("EXPLICIT")
        );

        verify(musicRepository, times(2)).save(any(MusicEntity.class));
    }

    @Test
    @DisplayName("uploadMusics: uploaderId가 제대로 설정되는지 확인한다")
    void uploadMusics_setsUploaderIdCorrectly() {
        // given
        Long expectedUploaderId = 123L;
        when(customUserDetails.getId()).thenReturn(expectedUploaderId);
        when(authentication.getPrincipal()).thenReturn(customUserDetails);
        
        // ArgumentCaptor를 사용하여 저장되는 엔티티를 캡처
        ArgumentCaptor<MusicEntity> musicEntityCaptor = ArgumentCaptor.forClass(MusicEntity.class);
        
        when(musicRepository.save(any(MusicEntity.class))).thenAnswer(invocation -> {
            MusicEntity toSave = invocation.getArgument(0);
            return MusicEntity.builder()
                    .id(1L)
                    .title(toSave.getTitle())
                    .artist(toSave.getArtist())
                    .album(toSave.getAlbum())
                    .genre(toSave.getGenre())
                    .releaseDate(toSave.getReleaseDate())
                    .duration(toSave.getDuration())
                    .musicUrl(toSave.getMusicUrl())
                    .albumImageUrl(toSave.getAlbumImageUrl())
                    .uploaderName(toSave.getUploaderName())
                    .uploaderId(toSave.getUploaderId())
                    .playCount(toSave.getPlayCount())
                    .likeCount(toSave.getLikeCount())
                    .isPublic(toSave.getIsPublic())
                    .fileSize(toSave.getFileSize())
                    .fileType(toSave.getFileType())
                    .musicGrade(toSave.getMusicGrade())
                    .build();
        });

        // when
        List<MusicResponseDto> responses = musicService.uploadMusics(List.of(sampleDto1), authentication);

        // then
        assertThat(responses).hasSize(1);
        
        // repository.save가 호출될 때 전달된 엔티티를 캡처하여 uploaderId 확인
        verify(musicRepository, times(1)).save(musicEntityCaptor.capture());
        
        MusicEntity capturedEntity = musicEntityCaptor.getValue();
        assertThat(capturedEntity.getUploaderId()).isEqualTo(expectedUploaderId);
        assertThat(capturedEntity.getTitle()).isEqualTo(sampleDto1.getTitle());
        assertThat(capturedEntity.getArtist()).isEqualTo(sampleDto1.getArtist());
    }

    @Test
    @DisplayName("uploadMusics: 저장 중 예외가 발생하면 GlobalException을 던진다")
    void uploadMusics_failure_throwsGlobalException() {
        when(musicRepository.save(any(MusicEntity.class)))
                .thenThrow(new RuntimeException("DB down"));

        GlobalException thrown = assertThrows(GlobalException.class,
                () -> musicService.uploadMusics(List.of(sampleDto1), authentication));

        assertAll(
                () -> assertThat(thrown.getMessage()).isEqualTo("음악 업로드에 실패했습니다."),
                () -> assertThat(thrown.getErrorCode()).isEqualTo("MUSIC_UPLOAD_FAILED")
        );
    }
}


