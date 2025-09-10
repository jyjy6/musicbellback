package MusicBellBackEnd.MusicBellBackEnd.Music;

import MusicBellBackEnd.MusicBellBackEnd.Artist.ArtistService;
import MusicBellBackEnd.MusicBellBackEnd.Auth.CustomUserDetails;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Music.Dto.MusicRequestDto;
import MusicBellBackEnd.MusicBellBackEnd.Music.Dto.MusicResponseDto;
import MusicBellBackEnd.MusicBellBackEnd.Redis.PlaylistService;
import MusicBellBackEnd.MusicBellBackEnd.Redis.RankingService;
import MusicBellBackEnd.MusicBellBackEnd.Redis.RecentPlayService;
import MusicBellBackEnd.MusicBellBackEnd.Redis.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

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

    // 추가 Mock 객체들 - MusicService의 의존성들
    @Mock
    private ArtistService artistService;
    
    @Mock
    private RedisService redisService;
    
    @Mock
    private RankingService rankingService;
    
    @Mock
    private PlaylistService playlistService;
    
    @Mock
    private RecentPlayService recentPlayService;

    @InjectMocks
    private MusicService musicService;

    private MusicRequestDto sampleDto1;
    private MusicRequestDto sampleDto2;

    // 테스트용 상수 정의
    private static final Long TEST_USER_ID = 123L;
    private static final String TEST_UPLOADER_NAME = "tester";
    private static final Long TEST_MUSIC_ID = 1L;
    
    // 테스트용 샘플 엔티티
    private MusicEntity sampleEntity;
    
    @BeforeEach
    void setUp() {
        // === 1. Mock 객체 공통 설정 (모든 테스트에서 필요) ===
        setupAuthenticationMocks();
        
        // === 2. 테스트용 샘플 데이터 생성 (여러 테스트에서 재사용) ===
        createSampleDtos();
        createSampleEntity();
        
        // === 3. 기본 Mock 동작 설정 ===
        setupDefaultMockBehaviors();
    }
    
    private void setupAuthenticationMocks() {
        // 인증 관련 Mock 설정 - 모든 테스트에서 동일하게 사용
        when(customUserDetails.getId()).thenReturn(TEST_USER_ID);
        when(authentication.getPrincipal()).thenReturn(customUserDetails);
        when(authentication.isAuthenticated()).thenReturn(true);
    }
    
    private void createSampleDtos() {
        sampleDto1 = MusicRequestDto.builder()
                .title("Song A")
                .artist("Artist A")
                .album("Album A")
                .genre("POP")
                .releaseDate(LocalDate.of(2024, 1, 1))
                .duration(180)
                .musicUrl("https://s3/test/song-a.mp3")
                .albumImageUrl("https://s3/test/album-a.jpg")
                .uploaderName(TEST_UPLOADER_NAME)
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
                .uploaderName(TEST_UPLOADER_NAME)
                .isPublic(Boolean.TRUE)
                .fileSize(654321L)
                .fileType("mp3")
                .musicGrade("EXPLICIT")
                .build();
    }
    
    private void createSampleEntity() {
        sampleEntity = MusicEntity.builder()
                .id(TEST_MUSIC_ID)
                .title("Sample Song")
                .artist("Sample Artist")
                .album("Sample Album")
                .genre("POP")
                .releaseDate(LocalDate.of(2024, 1, 1))
                .duration(180)
                .musicUrl("https://s3/test/sample.mp3")
                .albumImageUrl("https://s3/test/sample.jpg")
                .uploaderName(TEST_UPLOADER_NAME)
                .uploaderId(TEST_USER_ID)
                .playCount(100L)
                .likeCount(50L)
                .isPublic(true)
                .fileSize(123456L)
                .fileType("mp3")
                .musicGrade("GENERAL")
                .build();
    }
    
    private void setupDefaultMockBehaviors() {
        // 기본적인 Mock 동작 설정 - 필요시 개별 테스트에서 override 가능
        // void 메서드들은 doNothing()으로 설정
        doNothing().when(playlistService).addToPlaylist(any(), any(), any(), any());
        doNothing().when(recentPlayService).addRecentPlay(any(), any(), any(), any(), any(), any());
        doNothing().when(redisService).incrementHashValue(any(), any(), any());
        doNothing().when(rankingService).updatePlayScore(any(), any());
        doNothing().when(artistService).updateArtistStats(any(), any(), any());
        
        // 기본적으로 빈 Optional 반환하도록 설정
        when(musicRepository.findById(any(Long.class)))
                .thenReturn(java.util.Optional.empty());
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

    // ===== getMusicById 테스트 =====
    
    @Test
    @DisplayName("getMusicById: 존재하는 음악 ID로 조회 시 성공적으로 반환한다")
    void getMusicById_success() {
        // given
        when(musicRepository.findById(TEST_MUSIC_ID))
                .thenReturn(java.util.Optional.of(sampleEntity));
        
        // when
        MusicResponseDto result = musicService.getMusicById(TEST_MUSIC_ID, authentication);
        
        // then
        assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getId()).isEqualTo(TEST_MUSIC_ID),
                () -> assertThat(result.getTitle()).isEqualTo(sampleEntity.getTitle()),
                () -> assertThat(result.getArtist()).isEqualTo(sampleEntity.getArtist())
        );
        
        // 플레이리스트와 최근 재생 목록 추가 검증
        verify(playlistService, times(1)).addToPlaylist(
                eq(TEST_USER_ID), eq(TEST_MUSIC_ID), eq(sampleEntity.getTitle()), eq(sampleEntity.getMusicUrl())
        );
        verify(recentPlayService, times(1)).addRecentPlay(
                eq(TEST_USER_ID), eq(TEST_MUSIC_ID), eq(sampleEntity.getTitle()), 
                eq(sampleEntity.getAlbumImageUrl()), eq(sampleEntity.getArtist()), eq(sampleEntity.getDuration())
        );
    }
    
    @Test
    @DisplayName("getMusicById: 존재하지 않는 음악 ID로 조회 시 GlobalException을 던진다")
    void getMusicById_notFound_throwsGlobalException() {
        // given
        when(musicRepository.findById(TEST_MUSIC_ID))
                .thenReturn(java.util.Optional.empty());
        
        // when & then
        GlobalException thrown = assertThrows(GlobalException.class,
                () -> musicService.getMusicById(TEST_MUSIC_ID, authentication));
        
        assertAll(
                () -> assertThat(thrown.getMessage()).isEqualTo("음악을 찾을 수 없습니다."),
                () -> assertThat(thrown.getErrorCode()).isEqualTo("MUSIC_NOT_FOUND")
        );
    }
    
    @Test
    @DisplayName("getMusicById: 인증되지 않은 사용자는 플레이리스트 추가 없이 음악 조회만 가능하다")
    void getMusicById_unauthenticated_success() {
        // given
        when(musicRepository.findById(TEST_MUSIC_ID))
                .thenReturn(java.util.Optional.of(sampleEntity));
        when(authentication.isAuthenticated()).thenReturn(false);
        
        // when
        MusicResponseDto result = musicService.getMusicById(TEST_MUSIC_ID, authentication);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(TEST_MUSIC_ID);
        
        // 플레이리스트와 최근 재생 목록 추가가 호출되지 않았는지 검증
        verify(playlistService, times(0)).addToPlaylist(any(), any(), any(), any());
        verify(recentPlayService, times(0)).addRecentPlay(any(), any(), any(), any(), any(), any());
    }
    
    @Test
    @DisplayName("getMusicById: 플레이리스트 추가 실패해도 음악 조회는 성공한다")
    void getMusicById_playlistAddFails_musicRetrievalSucceeds() {
        // given
        when(musicRepository.findById(TEST_MUSIC_ID))
                .thenReturn(java.util.Optional.of(sampleEntity));
        // void 메서드에 예외 발생 설정
        doThrow(new GlobalException("플레이리스트 서비스 오류", "PLAYLIST_SAVE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR))
                .when(playlistService).addToPlaylist(any(), any(), any(), any());
        
        // when
        MusicResponseDto result = musicService.getMusicById(TEST_MUSIC_ID, authentication);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(TEST_MUSIC_ID);
    }

    // ===== getMusicByIdWithoutIncrement 테스트 =====
    
    @Test
    @DisplayName("getMusicByIdWithoutIncrement: 재생 카운트 증가 없이 음악 정보를 조회한다")
    void getMusicByIdWithoutIncrement_success() {
        // given
        when(musicRepository.findById(TEST_MUSIC_ID))
                .thenReturn(java.util.Optional.of(sampleEntity));
        
        // when
        MusicResponseDto result = musicService.getMusicByIdWithoutIncrement(TEST_MUSIC_ID);
        
        // then
        assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getId()).isEqualTo(TEST_MUSIC_ID),
                () -> assertThat(result.getTitle()).isEqualTo(sampleEntity.getTitle())
        );
        
        // incrementPlayCount 관련 메서드들이 호출되지 않았는지 확인
        verify(redisService, times(0)).incrementHashValue(any(), any(), any());
        verify(rankingService, times(0)).updatePlayScore(any(), any());
    }

    // ===== toggleLike 테스트 =====
    
    @Test
    @DisplayName("toggleLike: 좋아요 추가 시 카운트가 증가한다")
    void toggleLike_addLike_success() {
        // given
        when(musicRepository.findById(TEST_MUSIC_ID))
                .thenReturn(java.util.Optional.of(sampleEntity));
        
        // when
        boolean result = musicService.toggleLike(TEST_MUSIC_ID, true);
        
        // then
        assertThat(result).isTrue();
        verify(musicRepository, times(1)).incrementLikeCount(TEST_MUSIC_ID);
        verify(musicRepository, times(0)).decrementLikeCount(any());
    }
    
    @Test
    @DisplayName("toggleLike: 좋아요 취소 시 카운트가 감소한다")
    void toggleLike_removeLike_success() {
        // given
        when(musicRepository.findById(TEST_MUSIC_ID))
                .thenReturn(java.util.Optional.of(sampleEntity));
        
        // when
        boolean result = musicService.toggleLike(TEST_MUSIC_ID, false);
        
        // then
        assertThat(result).isFalse();
        verify(musicRepository, times(1)).decrementLikeCount(TEST_MUSIC_ID);
        verify(musicRepository, times(0)).incrementLikeCount(any());
    }

    // ===== incrementPlayCount 테스트 =====
    
    @Test
    @DisplayName("incrementPlayCount: 재생 카운트와 관련 서비스들이 정상적으로 업데이트된다")
    void incrementPlayCount_success() {
        // given
        MusicEntity entityWithPlayCount = MusicEntity.builder()
                .id(TEST_MUSIC_ID)
                .title("Sample Song")
                .playCount(100L)
                .build();
        
        when(musicRepository.findById(TEST_MUSIC_ID))
                .thenReturn(java.util.Optional.of(entityWithPlayCount));
        when(musicRepository.save(any(MusicEntity.class)))
                .thenReturn(entityWithPlayCount);
        
        // when
        musicService.incrementPlayCount(TEST_MUSIC_ID);
        
        // then
        verify(musicRepository, times(1)).save(any(MusicEntity.class));
        verify(redisService, times(1)).incrementHashValue("music:stats:" + TEST_MUSIC_ID, "playCount", 1);
        verify(rankingService, times(1)).updatePlayScore("music", TEST_MUSIC_ID);
    }
    
    @Test
    @DisplayName("incrementPlayCount: 존재하지 않는 음악 ID로 호출 시 GlobalException을 던진다")
    void incrementPlayCount_notFound_throwsGlobalException() {
        // given
        when(musicRepository.findById(TEST_MUSIC_ID))
                .thenReturn(java.util.Optional.empty());
        
        // when & then
        GlobalException thrown = assertThrows(GlobalException.class,
                () -> musicService.incrementPlayCount(TEST_MUSIC_ID));
        
        assertAll(
                () -> assertThat(thrown.getMessage()).isEqualTo("음악을 찾을 수 없습니다."),
                () -> assertThat(thrown.getErrorCode()).isEqualTo("NOT_MUSIC_FOUND")
        );
    }






}


