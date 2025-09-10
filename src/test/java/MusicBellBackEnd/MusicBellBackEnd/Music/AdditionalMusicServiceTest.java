package MusicBellBackEnd.MusicBellBackEnd.Music;

import MusicBellBackEnd.MusicBellBackEnd.Music.Dto.MusicResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

/**
 * MusicService 추가 테스트 케이스들
 * 복잡한 시나리오와 경계값 테스트를 포함
 */
@ExtendWith(MockitoExtension.class)
class AdditionalMusicServiceTest {

    @Mock
    private MusicRepository musicRepository;

    @InjectMocks
    private MusicService musicService;

    @Test
    @DisplayName("getMusicsByIds: 여러 ID로 배치 조회 시 순서를 유지하여 반환한다")
    void getMusicsByIds_maintainsOrder() {
        // given
        List<Long> musicIds = List.of(1L, 2L, 3L);
        
        MusicEntity music1 = MusicEntity.builder().id(1L).title("Song 1").artist("Artist 1").build();
        MusicEntity music2 = MusicEntity.builder().id(2L).title("Song 2").artist("Artist 2").build();
        MusicEntity music3 = MusicEntity.builder().id(3L).title("Song 3").artist("Artist 3").build();
        
        // 순서가 섞인 상태로 반환 (실제 DB에서는 순서가 보장되지 않을 수 있음)
        when(musicRepository.findAllById(musicIds))
                .thenReturn(List.of(music3, music1, music2));
        
        // when
        List<MusicResponseDto> results = musicService.getMusicsByIds(musicIds);
        
        // then - 원래 요청한 순서대로 반환되는지 확인
        assertAll(
                () -> assertThat(results).hasSize(3),
                () -> assertThat(results.get(0).getId()).isEqualTo(1L), // 원래 순서 유지
                () -> assertThat(results.get(1).getId()).isEqualTo(2L),
                () -> assertThat(results.get(2).getId()).isEqualTo(3L),
                () -> assertThat(results.get(0).getTitle()).isEqualTo("Song 1"),
                () -> assertThat(results.get(1).getTitle()).isEqualTo("Song 2"),
                () -> assertThat(results.get(2).getTitle()).isEqualTo("Song 3")
        );
    }
}
