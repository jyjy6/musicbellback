package MusicBellBackEnd.MusicBellBackEnd.Artist.ElasticSearch;

import co.elastic.clients.elasticsearch._types.FieldValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistSearchService {

    private final ElasticsearchTemplate elasticsearchTemplate;

    /**
     * 🎯 스마트 아티스트 검색 (페이지네이션 지원)
     * - 아티스트명, 소개, 소속사, 장르에서 검색
     * - 오타 허용, 부분 검색 지원
     * - 인기도 기반 정렬
     * - 페이지네이션 지원
     */
    public Page<ArtistDocument> smartSearch(String keyword, String genre, String country, Boolean isVerified, int page, int size) {
        try {
            // 입력 검증
            if (page < 0) {
                throw new GlobalException("페이지 번호는 0 이상이어야 합니다.", "INVALID_PAGE_NUMBER", HttpStatus.BAD_REQUEST);
            }
            if (size <= 0 || size > 100) {
                throw new GlobalException("페이지 크기는 1-100 사이여야 합니다.", "INVALID_PAGE_SIZE", HttpStatus.BAD_REQUEST);
            }
            
            log.info("🚀 아티스트 검색 시작: keyword={}, genre={}, country={}, verified={}, page={}, size={}", 
                    keyword, genre, country, isVerified, page, size);
            
            Pageable pageable = PageRequest.of(page, size);
            
            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> {
                        // should 조건들 (OR 검색)
                        if (keyword != null && !keyword.trim().isEmpty()) {
                            b.should(s -> s.multiMatch(m -> m
                                    .query(keyword)
                                    .fields("name^3", "description^2", "searchText^1.5") // 아티스트명에 가장 높은 가중치
                                    .boost(3.0f)
                            ))
                            // 오타 허용 검색
                            .should(s -> s.multiMatch(m -> m
                                    .query(keyword)
                                    .fields("name^2", "description^1.5", "agency^1")
                                    .fuzziness("AUTO") // 오타 허용
                                    .boost(2.0f)
                            ))
                            // 부분 검색 (와일드카드)
                            .should(s -> s.wildcard(w -> w
                                    .field("name")
                                    .value("*" + keyword.toLowerCase() + "*")
                                    .boost(1.5f)
                            ))
                            // 소속사 검색
                            .should(s -> s.match(m -> m
                                    .field("agency")
                                    .query(keyword)
                                    .boost(1.0f)
                            ))
                            .minimumShouldMatch("1"); // 최소 하나는 매치
                        }

                        // must 조건들 (AND 필터)
                        
                        if (genre != null) {
                            b.must(m -> m.term(t -> t.field("genre").value(genre)));
                        }
                        if (country != null) {
                            b.must(m -> m.term(t -> t.field("country").value(country)));
                        }
                        if (isVerified != null) {
                            b.must(m -> m.term(t -> t.field("isVerified").value(isVerified)));
                        }

                        return b;
                    }))
                    .withSort(Sort.by(Sort.Direction.DESC, "_score")) // 관련도 순 (1차)
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore")) // 인기도 순 (2차)
                    .withSort(Sort.by(Sort.Direction.DESC, "followerCount")) // 팔로워 순 (3차)
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);

            List<ArtistDocument> results = searchHits.stream()
                    .map(hit -> {
                        ArtistDocument artist = hit.getContent();
                        log.info("🎯 점수: {:.2f} - 아티스트: {} (장르: {})",
                                hit.getScore(), artist.getName(), artist.getGenre());
                        return artist;
                    })
                    .collect(Collectors.toList());

            Page<ArtistDocument> resultPage = new PageImpl<>(results, pageable, searchHits.getTotalHits());

            log.info("🚀 아티스트 검색 완료: {} 개 결과 (전체: {}, 페이지: {}/{})", 
                    results.size(), searchHits.getTotalHits(), page + 1, resultPage.getTotalPages());
            
            return resultPage;

        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("🚨 아티스트 검색 실패: ", e);
            throw new GlobalException("아티스트 검색 중 오류가 발생했습니다.", "ARTIST_SEARCH_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 🎵 장르별 아티스트 검색 (페이지네이션 지원)
     */
    public Page<ArtistDocument> searchByGenre(String genre, int page, int size) {
        try {
            if (genre == null || genre.trim().isEmpty()) {
                throw new GlobalException("장르는 필수입니다.", "GENRE_REQUIRED", HttpStatus.BAD_REQUEST);
            }
            if (page < 0) {
                throw new GlobalException("페이지 번호는 0 이상이어야 합니다.", "INVALID_PAGE_NUMBER", HttpStatus.BAD_REQUEST);
            }
            if (size <= 0 || size > 100) {
                throw new GlobalException("페이지 크기는 1-100 사이여야 합니다.", "INVALID_PAGE_SIZE", HttpStatus.BAD_REQUEST);
            }
            
            log.info("🎵 장르별 검색: {}, page={}, size={}", genre, page, size);

            Pageable pageable = PageRequest.of(page, size);

            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("genre").value(genre)))
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore"))
                    .withSort(Sort.by(Sort.Direction.DESC, "followerCount"))
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            List<ArtistDocument> results = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("🚨 장르별 검색 실패: ", e);
            throw new GlobalException("장르별 검색 중 오류가 발생했습니다.", "GENRE_SEARCH_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 🏆 인기 아티스트 검색 (페이지네이션 지원)
     */
    public Page<ArtistDocument> getPopularArtists(int page, int size) {
        try {
            if (page < 0) {
                throw new GlobalException("페이지 번호는 0 이상이어야 합니다.", "INVALID_PAGE_NUMBER", HttpStatus.BAD_REQUEST);
            }
            if (size <= 0 || size > 100) {
                throw new GlobalException("페이지 크기는 1-100 사이여야 합니다.", "INVALID_PAGE_SIZE", HttpStatus.BAD_REQUEST);
            }
            
            Pageable pageable = PageRequest.of(page, size);

            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore"))
                    .withSort(Sort.by(Sort.Direction.DESC, "followerCount"))
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            List<ArtistDocument> results = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("🚨 인기 아티스트 검색 실패: ", e);
            throw new GlobalException("인기 아티스트 조회 중 오류가 발생했습니다.", "POPULAR_ARTISTS_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * ✅ 인증 아티스트 검색 (페이지네이션 지원)
     */
    public Page<ArtistDocument> getVerifiedArtists(int page, int size) {
        try {
            if (page < 0) {
                throw new GlobalException("페이지 번호는 0 이상이어야 합니다.", "INVALID_PAGE_NUMBER", HttpStatus.BAD_REQUEST);
            }
            if (size <= 0 || size > 100) {
                throw new GlobalException("페이지 크기는 1-100 사이여야 합니다.", "INVALID_PAGE_SIZE", HttpStatus.BAD_REQUEST);
            }
            
            Pageable pageable = PageRequest.of(page, size);

            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("isVerified").value(true)))
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "followerCount"))
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore"))
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            List<ArtistDocument> results = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("🚨 인증 아티스트 검색 실패: ", e);
            throw new GlobalException("인증 아티스트 조회 중 오류가 발생했습니다.", "VERIFIED_ARTISTS_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 🆕 신규 아티스트 검색 (페이지네이션 지원)
     */
    public Page<ArtistDocument> getRecentArtists(int page, int size) {
        try {
            if (page < 0) {
                throw new GlobalException("페이지 번호는 0 이상이어야 합니다.", "INVALID_PAGE_NUMBER", HttpStatus.BAD_REQUEST);
            }
            if (size <= 0 || size > 100) {
                throw new GlobalException("페이지 크기는 1-100 사이여야 합니다.", "INVALID_PAGE_SIZE", HttpStatus.BAD_REQUEST);
            }
            
            Pageable pageable = PageRequest.of(page, size);

            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "createdAt"))
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            List<ArtistDocument> results = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("🚨 신규 아티스트 검색 실패: ", e);
            throw new GlobalException("신규 아티스트 조회 중 오류가 발생했습니다.", "RECENT_ARTISTS_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 🌍 국가별 아티스트 검색 (페이지네이션 지원)
     */
    public Page<ArtistDocument> searchByCountry(String country, int page, int size) {
        try {
            if (country == null || country.trim().isEmpty()) {
                throw new GlobalException("국가는 필수입니다.", "COUNTRY_REQUIRED", HttpStatus.BAD_REQUEST);
            }
            if (page < 0) {
                throw new GlobalException("페이지 번호는 0 이상이어야 합니다.", "INVALID_PAGE_NUMBER", HttpStatus.BAD_REQUEST);
            }
            if (size <= 0 || size > 100) {
                throw new GlobalException("페이지 크기는 1-100 사이여야 합니다.", "INVALID_PAGE_SIZE", HttpStatus.BAD_REQUEST);
            }
            
            Pageable pageable = PageRequest.of(page, size);

            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("country").value(country)))
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore"))
                    .withSort(Sort.by(Sort.Direction.DESC, "followerCount"))
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            List<ArtistDocument> results = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("🚨 국가별 검색 실패: ", e);
            throw new GlobalException("국가별 검색 중 오류가 발생했습니다.", "COUNTRY_SEARCH_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 🔍 아티스트명 자동완성
     */
    public List<String> autoComplete(String prefix, int size) {
        try {
            if (prefix == null || prefix.trim().isEmpty()) {
                throw new GlobalException("검색어는 필수입니다.", "PREFIX_REQUIRED", HttpStatus.BAD_REQUEST);
            }
            if (size <= 0 || size > 50) {
                throw new GlobalException("결과 크기는 1-50 사이여야 합니다.", "INVALID_SIZE", HttpStatus.BAD_REQUEST);
            }
            
            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.prefix(p -> p
                                    .field("name")
                                    .value(prefix.toLowerCase())
                            ))
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore"))
                    .withMaxResults(size)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            return searchHits.stream()
                    .map(hit -> hit.getContent().getName())
                    .distinct()
                    .collect(Collectors.toList());

        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("🚨 자동완성 실패: ", e);
            throw new GlobalException("자동완성 조회 중 오류가 발생했습니다.", "AUTOCOMPLETE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 📊 장르별 통계 조회
     */
    public List<GenreStats> getGenreStats() {
        try {
            // 이 부분은 집계 쿼리로 구현할 수 있지만, 간단히 모든 활성 아티스트를 가져와서 처리
            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                    ))
                    .withMaxResults(10000) // 충분히 큰 수
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            
            return searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.groupingBy(
                            artist -> artist.getGenre() != null ? artist.getGenre() : "기타",
                            Collectors.counting()
                    ))
                    .entrySet().stream()
                    .map(entry -> new GenreStats(entry.getKey(), entry.getValue()))
                    .sorted((a, b) -> Long.compare(b.count, a.count))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("🚨 장르별 통계 조회 실패: ", e);
            throw new GlobalException("장르별 통계 조회 중 오류가 발생했습니다.", "GENRE_STATS_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 장르별 통계 정보 클래스
     */
    public static class GenreStats {
        public final String genre;
        public final long count;
        
        public GenreStats(String genre, long count) {
            this.genre = genre;
            this.count = count;
        }
    }
}
