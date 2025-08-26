package MusicBellBackEnd.MusicBellBackEnd.Artist.ElasticSearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ArtistSearchRepository extends ElasticsearchRepository<ArtistDocument, String> {
}
