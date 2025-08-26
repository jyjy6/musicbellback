package MusicBellBackEnd.MusicBellBackEnd.Artist;

import MusicBellBackEnd.MusicBellBackEnd.Artist.Dto.ArtistPageResponseDto;
import MusicBellBackEnd.MusicBellBackEnd.Artist.Dto.ArtistRequestDto;
import MusicBellBackEnd.MusicBellBackEnd.Artist.Dto.ArtistResponseDto;
import MusicBellBackEnd.MusicBellBackEnd.Artist.Dto.ArtistSearchDto;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistService {
    
    private final ArtistRepository artistRepository;
    
    /**
     * 아티스트명으로 찾기 또는 새로 생성
     * 기존 데이터 마이그레이션을 위한 핵심 메소드
     */
    @Transactional
    public ArtistEntity findOrCreateArtist(String artistName) {
        if (artistName == null || artistName.trim().isEmpty()) {
            throw new GlobalException("아티스트명은 필수입니다.", "ARTISTNAME_NEEDED_ERROR");
        }
        
        String trimmedName = artistName.trim();
        
        // 1. 정확히 일치하는 아티스트 찾기
        Optional<ArtistEntity> existingArtist = artistRepository.findByNameIgnoreCase(trimmedName);
        if (existingArtist.isPresent()) {
            log.debug("기존 아티스트 발견: {}", trimmedName);
            return existingArtist.get();
        }
        
        // 2. 유사한 이름의 아티스트 체크 (오타 방지)
        List<ArtistEntity> similarArtists = artistRepository.findByNameContainingIgnoreCase(trimmedName);
        if (!similarArtists.isEmpty()) {
            // 정확히 일치하는 것이 있는지 다시 한번 체크
            for (ArtistEntity similar : similarArtists) {
                if (similar.getName().equalsIgnoreCase(trimmedName)) {
                    log.debug("유사 검색에서 정확한 매치 발견: {}", trimmedName);
                    return similar;
                }
            }
            
            // TODO: 나중에 유사도 계산 로직 추가 가능
            log.info("유사한 아티스트 발견되었지만 정확한 매치 없음: {} (유사: {})", 
                    trimmedName, similarArtists.get(0).getName());
        }
        
        // 3. 새 아티스트 생성
        ArtistEntity newArtist = ArtistEntity.builder()
                .name(trimmedName)
                .isVerified(false)
                .isActive(true)
                .followerCount(0L)
                .totalPlayCount(0L)
                .totalLikeCount(0L)
                .build();
                
        ArtistEntity savedArtist = artistRepository.save(newArtist);
        log.info("새 아티스트 생성: {} (ID: {})", trimmedName, savedArtist.getId());
        
        return savedArtist;
    }
    
    /**
     * 유사한 아티스트명 찾기 (자동완성용)
     */
    public List<ArtistEntity> findSimilarArtists(String partialName, int limit) {
        if (partialName == null || partialName.trim().length() < 2) {
            return List.of();
        }
        
        List<ArtistEntity> similar = artistRepository.findByNameContainingIgnoreCase(partialName.trim());
        return similar.stream()
                .filter(ArtistEntity::getIsActive)
                .limit(limit)
                .toList();
    }
    
    /**
     * 아티스트 통계 업데이트 (음악 재생/좋아요 시 호출)
     */
    @Transactional
    public void updateArtistStats(Long artistId, Long playCountDelta, Long likeCountDelta) {
        if (playCountDelta != null && playCountDelta != 0) {
            artistRepository.updateTotalPlayCount(artistId, playCountDelta);
        }
        if (likeCountDelta != null && likeCountDelta != 0) {
            artistRepository.updateTotalLikeCount(artistId, likeCountDelta);
        }
    }
    
    /**
     * 아티스트 존재 여부 확인
     */
    public boolean existsByName(String name) {
        return artistRepository.existsByNameIgnoreCase(name);
    }
    
    /**
     * 아티스트 조회 (ID로)
     */
    public Optional<ArtistEntity> findById(Long id) {
        return artistRepository.findById(id);
    }

    // === CRUD 메소드들 ===

    /**
     * 아티스트 생성
     */
    @Transactional
    public ArtistResponseDto createArtist(ArtistRequestDto requestDto) {
        // 유효성 검사
        validateArtistRequest(requestDto);

        // 중복 체크
        if (artistRepository.existsByNameIgnoreCase(requestDto.getName())) {
            throw new IllegalArgumentException("이미 등록된 아티스트명입니다: " + requestDto.getName());
        }

        // 엔티티 생성
        ArtistEntity artistEntity = ArtistEntity.builder()
                .name(requestDto.getName().trim())
                .description(requestDto.getDescription())
                .profileImageUrl(requestDto.getProfileImageUrl())
                .genre(requestDto.getGenre())
                .country(requestDto.getCountry())
                .agency(requestDto.getAgency())
                .officialWebsite(requestDto.getOfficialWebsite())
                .instagramHandle(requestDto.getInstagramHandle())
                .twitterHandle(requestDto.getTwitterHandle())
                .spotifyId(requestDto.getSpotifyId())
                .appleMusicId(requestDto.getAppleMusicId())
                .youtubeChannelId(requestDto.getYoutubeChannelId())
                .isVerified(false)
                .isActive(true)
                .followerCount(0L)
                .totalPlayCount(0L)
                .totalLikeCount(0L)
                .build();

        ArtistEntity savedArtist = artistRepository.save(artistEntity);
        log.info("아티스트 생성 완료: {} (ID: {})", savedArtist.getName(), savedArtist.getId());

        return convertToResponseDto(savedArtist);
    }

    /**
     * 아티스트 상세 조회
     */
    public ArtistResponseDto getArtistById(Long id) {
        ArtistEntity artist = artistRepository.findById(id)
                .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));

        return convertToResponseDto(artist);
    }

    /**
     * 아티스트 목록 조회 (페이징)
     */
    public ArtistPageResponseDto getAllArtists(int page, int size, String sortBy, String sortOrder) {
        try {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder)
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;

            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            Page<ArtistEntity> artistPage = artistRepository.findByIsActiveTrueOrderByNameAsc(pageable);

            return convertToPageResponseDto(artistPage);
        } catch (Exception e) {
            log.error("아티스트 목록 조회 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("아티스트 목록 조회에 실패했습니다.", "ARTIST_LIST_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 아티스트 검색
     */
    public ArtistPageResponseDto searchArtists(ArtistSearchDto searchDto) {
        try {
            Sort.Direction direction = "asc".equalsIgnoreCase(searchDto.getSortOrder())
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;

            Pageable pageable = PageRequest.of(
                    searchDto.getPage(),
                    searchDto.getSize(),
                    Sort.by(direction, searchDto.getSortBy())
            );

            Page<ArtistEntity> artistPage = artistRepository.searchArtists(
                    searchDto.getName(),
                    searchDto.getGenre(),
                    searchDto.getCountry(),
                    searchDto.getIsVerified(),
                    pageable
            );

            return convertToPageResponseDto(artistPage);
        } catch (Exception e) {
            log.error("아티스트 검색 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("아티스트 검색에 실패했습니다.", "ARTIST_SEARCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 아티스트 수정
     */
    @Transactional
    public ArtistResponseDto updateArtist(Long id, ArtistRequestDto requestDto) {
        ArtistEntity artist = artistRepository.findById(id)
                .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));

        // 이름 변경 시 중복 체크
        if (!artist.getName().equalsIgnoreCase(requestDto.getName()) && 
            artistRepository.existsByNameIgnoreCase(requestDto.getName())) {
            throw new IllegalArgumentException("이미 등록된 아티스트명입니다: " + requestDto.getName());
        }

        // 업데이트
        updateArtistEntity(artist, requestDto);
        ArtistEntity savedArtist = artistRepository.save(artist);

        log.info("아티스트 ID {} 정보가 수정되었습니다.", id);
        return convertToResponseDto(savedArtist);
    }

    /**
     * 아티스트 상태 토글 (활성화/비활성화)
     */
    @Transactional
    public boolean toggleArtistStatus(Long id, boolean isActive) {
        ArtistEntity artist = artistRepository.findById(id)
                .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));

        artist.setIsActive(isActive);
        artistRepository.save(artist);

        log.info("아티스트 ID {} 상태 변경: {}", id, isActive ? "활성화" : "비활성화");
        return isActive;
    }

    /**
     * 아티스트 인증 상태 토글
     */
    @Transactional
    public boolean toggleArtistVerification(Long id, boolean isVerified) {
        ArtistEntity artist = artistRepository.findById(id)
                .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));

        artist.setIsVerified(isVerified);
        artistRepository.save(artist);

        log.info("아티스트 ID {} 인증 상태 변경: {}", id, isVerified ? "인증" : "인증 해제");
        return isVerified;
    }

    /**
     * 아티스트 삭제 (소프트 삭제)
     */
    @Transactional
    public void deleteArtist(Long id) {
        ArtistEntity artist = artistRepository.findById(id)
                .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));

        artist.setIsActive(false);
        artistRepository.save(artist);

        log.info("아티스트 ID {}가 삭제되었습니다 (소프트 삭제).", id);
    }

    /**
     * 아티스트 자동완성 제안
     */
    public List<ArtistResponseDto> getArtistSuggestions(String query, int limit) {
        List<ArtistEntity> suggestions = findSimilarArtists(query, limit);
        return suggestions.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * 인기 아티스트 조회
     */
    public List<ArtistResponseDto> getPopularArtists() {
        List<ArtistEntity> popularArtists = artistRepository.findTop10ByIsActiveTrueOrderByFollowerCountDesc();
        return popularArtists.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * 최신 아티스트 조회
     */
    public List<ArtistResponseDto> getLatestArtists() {
        List<ArtistEntity> latestArtists = artistRepository.findTop10ByIsActiveTrueOrderByCreatedAtDesc();
        return latestArtists.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    // === 변환 메소드들 ===

    private ArtistResponseDto convertToResponseDto(ArtistEntity entity) {
        return ArtistResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .profileImageUrl(entity.getProfileImageUrl())
                .genre(entity.getGenre())
                .country(entity.getCountry())
                .agency(entity.getAgency())
                .isVerified(entity.getIsVerified())
                .isActive(entity.getIsActive())
                .followerCount(entity.getFollowerCount())
                .totalPlayCount(entity.getTotalPlayCount())
                .totalLikeCount(entity.getTotalLikeCount())
                .spotifyId(entity.getSpotifyId())
                .appleMusicId(entity.getAppleMusicId())
                .youtubeChannelId(entity.getYoutubeChannelId())
                .officialWebsite(entity.getOfficialWebsite())
                .instagramHandle(entity.getInstagramHandle())
                .twitterHandle(entity.getTwitterHandle())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ArtistPageResponseDto convertToPageResponseDto(Page<ArtistEntity> page) {
        List<ArtistResponseDto> content = page.getContent().stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        return ArtistPageResponseDto.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    private void updateArtistEntity(ArtistEntity entity, ArtistRequestDto dto) {
        if (dto.getName() != null) entity.setName(dto.getName().trim());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getProfileImageUrl() != null) entity.setProfileImageUrl(dto.getProfileImageUrl());
        if (dto.getGenre() != null) entity.setGenre(dto.getGenre());
        if (dto.getCountry() != null) entity.setCountry(dto.getCountry());
        if (dto.getAgency() != null) entity.setAgency(dto.getAgency());
        if (dto.getOfficialWebsite() != null) entity.setOfficialWebsite(dto.getOfficialWebsite());
        if (dto.getInstagramHandle() != null) entity.setInstagramHandle(dto.getInstagramHandle());
        if (dto.getTwitterHandle() != null) entity.setTwitterHandle(dto.getTwitterHandle());
        if (dto.getSpotifyId() != null) entity.setSpotifyId(dto.getSpotifyId());
        if (dto.getAppleMusicId() != null) entity.setAppleMusicId(dto.getAppleMusicId());
        if (dto.getYoutubeChannelId() != null) entity.setYoutubeChannelId(dto.getYoutubeChannelId());
    }

    private void validateArtistRequest(ArtistRequestDto requestDto) {
        if (requestDto.getName() == null || requestDto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("아티스트명은 필수입니다.");
        }
        if (requestDto.getName().trim().length() < 2) {
            throw new IllegalArgumentException("아티스트명은 최소 2글자 이상이어야 합니다.");
        }
        if (requestDto.getName().trim().length() > 200) {
            throw new IllegalArgumentException("아티스트명은 200글자를 초과할 수 없습니다.");
        }
    }
}
