package MusicBellBackEnd.MusicBellBackEnd.Lyrics;

import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto.LyricsGenerationRequest;
import MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto.TranscriptionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIWhisperLyricsService {
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Value("${openai.whisper.api.url:https://api.openai.com/v1/audio/transcriptions}")
    private String whisperApiUrl;


    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 음악 파일에서 가사 추출 (Speech-to-Text)
     */
    public TranscriptionResult generateLyricsWithSegments(LyricsGenerationRequest request) throws IOException {
        log.info("음악 파일에서 가사 추출 시작: musicUrl={}, title={}",
                request.getMusicUrl(), request.getMusicTitle());

        if (request.getMusicUrl() == null || request.getMusicUrl().trim().isEmpty()) {
            log.error("음악 URL이 비어있습니다.");
            throw new GlobalException(
                    "음악 URL이 비어있습니다",
                    "INVALID_MUSIC_URL",
                    org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }

        Path audioFile = null;
        try {
            // 1. 음악 파일 다운로드
            audioFile = downloadAudioFile(request.getMusicUrl());

            // 2. Whisper API로 transcription (선택된 언어 반영)
            String whisperLang = mapClientLanguageToWhisperCode(request.getTargetLanguage());
            TranscriptionResult result = performSpeechToTextVerbose(audioFile, whisperLang);

            if (result == null || (result.getFullText() == null || result.getFullText().trim().isEmpty())) {
                log.warn("Speech-to-Text 결과가 비어있습니다.");
                throw new GlobalException(
                        "Whisper 결과가 비어있습니다",
                        "WHISPER_EMPTY_RESULT",
                        org.springframework.http.HttpStatus.BAD_GATEWAY
                );
            }

            log.info("가사 추출 완료: {} 글자, 세그먼트 {}개",
                    result.getFullText().length(),
                    result.getSegments() != null ? result.getSegments().size() : 0);
            return result;

        } catch (Exception e) {
            log.error("가사 추출 중 오류 발생: {}", e.getMessage(), e);
            throw new GlobalException(
                    "가사 추출 중 오류",
                    "WHISPER_TRANSCRIBE_FAILED",
                    org.springframework.http.HttpStatus.BAD_GATEWAY
            );
        } finally {
            cleanupTempFiles(audioFile);
        }
    }


    /**
     * S3에서 음악 파일 다운로드
     */
    private Path downloadAudioFile(String musicUrl) throws IOException {
        log.info("음악 파일 다운로드 시작: {}", musicUrl);

        Path tempFile = Files.createTempFile("music_", ".mp3");

        try {
            // URL 인코딩 처리 (한글, 공백 등 특수문자 처리)
            String encodedUrl = encodeUrl(musicUrl);
            log.info("인코딩된 URL: {}", encodedUrl);

            // HTTP 연결 설정
            URL url = new URL(encodedUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();

            // User-Agent 설정 (일부 서버에서 요구)
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "*/*");
            connection.setConnectTimeout(30000); // 30초 연결 타임아웃
            connection.setReadTimeout(60000); // 60초 읽기 타임아웃

            // 응답 코드 확인
            int responseCode = connection.getResponseCode();
            log.info("HTTP 응답 코드: {}", responseCode);

            if (responseCode != 200) {
                throw new IOException("HTTP 오류: " + responseCode + " - " + connection.getResponseMessage());
            }

            // 파일 다운로드
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("음악 파일 다운로드 완료: {} (크기: {} bytes)", tempFile.toString(), Files.size(tempFile));
                return tempFile;
            }

        } catch (Exception e) {
            log.error("음악 파일 다운로드 실패: {}", e.getMessage(), e);

            // 임시 파일 정리
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException cleanupError) {
                log.warn("임시 파일 정리 실패: {}", cleanupError.getMessage());
            }

            throw new IOException("음악 파일 다운로드에 실패했습니다: " + e.getMessage());
        }
    }




    /**
     * OpenAI Whisper API 호출
     */
    private TranscriptionResult performSpeechToTextVerbose(Path audioFile, String languageCode) throws IOException {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            log.warn("OpenAI API 키가 설정되지 않았습니다.");
            throw new GlobalException(
                    "OpenAI API 키가 설정되지 않았습니다",
                    "OPENAI_API_KEY_MISSING",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        log.info("Whisper API 처리 시작: {}", audioFile.toString());

        try {
            long fileSize = Files.size(audioFile);
            long maxFileSize = 25 * 1024 * 1024; // 25MB - Whisper API 제한

            log.info("처리할 오디오 파일 크기: {} bytes ({} MB)", fileSize, fileSize / (1024 * 1024));

            if (fileSize > maxFileSize) {
                log.error("파일 크기가 Whisper API 제한(25MB)을 초과합니다: {} MB",
                        fileSize / (1024 * 1024));
                throw new GlobalException(
                        "파일 크기가 Whisper API 제한(25MB)을 초과합니다",
                        "WHISPER_FILE_TOO_LARGE",
                        org.springframework.http.HttpStatus.BAD_REQUEST
                );
            }

            // 직접 Whisper API 호출 (verbose_json)
            return callWhisperApiVerbose(audioFile, languageCode);

        } catch (Exception e) {
            log.error("Whisper API 호출 중 오류 발생: {}", e.getMessage(), e);
            if (e instanceof GlobalException) {
                throw e;
            }
            throw new GlobalException(
                    "Whisper API 호출 중 오류",
                    "WHISPER_CALL_FAILED",
                    org.springframework.http.HttpStatus.BAD_GATEWAY
            );
        }
    }
    
    /**
     * OpenAI Whisper API 직접 호출
     */
    private TranscriptionResult callWhisperApiVerbose(Path audioFile, String languageCode) throws IOException {
        log.info("Whisper API 호출 시작 (verbose_json), language={}", languageCode);
        
        // Multipart 요청 생성
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(openaiApiKey);
        
        // 파일 읽기
        byte[] audioBytes = Files.readAllBytes(audioFile);
        // Multipart body 생성
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        /* 파일 추가
         * ByteArrayResource 클래스는 기본적으로 getFilename()을 null 리턴하도록 되어 있어요.
         * 그런데 업로드할 때 파일 이름이 필요한 경우가 많습니다.
         * (예: 멀티파트 요청 전송 시 Content-Disposition: form-data; name="file"; filename="audio.mp3" 이런 헤더에 들어감)
         * 그래서 익명 클래스(anonymous class)를 만들면서 getFilename() 메서드를 오버라이드해서 파일명을 지정한 거예요.
         */
        ByteArrayResource fileResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "audio.mp3";
            }
        };

        body.add("file", fileResource);
        // 모델 및 설정
        body.add("model", "whisper-1");
        if (languageCode != null && !languageCode.isBlank()) {
            body.add("language", languageCode);
        }
        body.add("response_format", "verbose_json");
        body.add("temperature", "0"); // 일관된 결과를 위해
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity =
            new HttpEntity<>(body, headers);
        
        try {
            log.info("Whisper API 요청 전송: 파일 크기 {} bytes", audioBytes.length);
            
            ResponseEntity<String> response = restTemplate.exchange(
                whisperApiUrl, 
                HttpMethod.POST, 
                requestEntity, 
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                String bodyStr = response.getBody();
                if (bodyStr != null && !bodyStr.trim().isEmpty()) {
                    // verbose_json 파싱
                    return parseVerboseJson(bodyStr);
                } else {
                    log.warn("Whisper API 응답이 비어있습니다.");
                    throw new GlobalException(
                            "Whisper API 응답이 비어있습니다",
                            "WHISPER_EMPTY_RESPONSE",
                            HttpStatus.BAD_GATEWAY
                    );
                }
            } else {
                log.error("Whisper API 호출 실패: {}", response.getStatusCode());
                throw new GlobalException(
                        "Whisper API 호출 실패: " + response.getStatusCode(),
                        "WHISPER_BAD_STATUS",
                        HttpStatus.BAD_GATEWAY
                );
            }
            
        } catch (Exception e) {
            log.error("Whisper API 호출 실패: {}", e.getMessage(), e);
            if (e instanceof GlobalException) {
                throw e;
            }
            throw new GlobalException(
                    "Whisper API 호출 실패",
                    "WHISPER_CALL_FAILED",
                    org.springframework.http.HttpStatus.BAD_GATEWAY
            );
        }
    }

    /**
     * 프론트의 언어 코드(KO/EN/JP/CN/ES/FR)를 Whisper 코드(ko/en/ja/zh/es/fr)로 매핑
     * null 또는 알 수 없는 경우 null 반환하여 자동 감지 사용
     */
    private String mapClientLanguageToWhisperCode(String clientCode) {
        if (clientCode == null || clientCode.isBlank()) return null;
        String code = clientCode.trim().toUpperCase();
        return switch (code) {
            case "KO" -> "ko";
            case "EN" -> "en";
            case "JP" -> "ja";
            case "CN" -> "zh";
            case "ES" -> "es";
            case "FR" -> "fr";
            default -> null; // 자동 감지
        };
    }

    /**
     * verbose_json 응답 파서
     */
    private TranscriptionResult parseVerboseJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            String fullText = root.path("text").asText("");
            List<TranscriptionResult.Segment> segments = new ArrayList<>();
            JsonNode segmentsNode = root.path("segments");
            if (segmentsNode.isArray()) {
                int order = 0;
                for (JsonNode seg : segmentsNode) {
                    double start = seg.path("start").asDouble(0.0);
                    double end = seg.path("end").asDouble(0.0);
                    String text = seg.path("text").asText("").trim();
                    int startMs = (int) Math.round(start * 1000.0);
                    int endMs = (int) Math.round(end * 1000.0);
                    segments.add(TranscriptionResult.Segment.builder()
                            .order(order++)
                            .startMs(startMs)
                            .endMs(endMs)
                            .text(text)
                            .type("VOCAL")
                            .build());
                }
            }

            return TranscriptionResult.builder()
                    .fullText(fullText)
                    .segments(segments)
                    .build();
        } catch (Exception e) {
            log.warn("verbose_json 파싱 실패: {}", e.getMessage());
            return TranscriptionResult.builder().fullText("").segments(java.util.Collections.emptyList()).build();
        }
    }


    /**
     * 임시 파일 정리
     */
    private void cleanupTempFiles(Path... files) {
        for (Path file : files) {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                    log.debug("임시 파일 삭제: {}", file.toString());
                } catch (IOException e) {
                    log.warn("임시 파일 삭제 실패: {}", file.toString(), e);
                }
            }
        }
    }


    /**
     * URL 인코딩 처리 (한글, 공백 등 특수문자 처리)
     */
    private String encodeUrl(String url) {
        try {
            // URL을 파싱하여 각 부분을 인코딩
            URL parsedUrl = new URL(url);
            String protocol = parsedUrl.getProtocol();
            String host = parsedUrl.getHost();
            int port = parsedUrl.getPort();
            String path = parsedUrl.getPath();
            String query = parsedUrl.getQuery();
            
            // 경로 부분만 인코딩 (파일명 부분)
            String[] pathParts = path.split("/");
            StringBuilder encodedPath = new StringBuilder();
            
            for (String part : pathParts) {
                if (!part.isEmpty()) {
                    encodedPath.append("/").append(URLEncoder.encode(part, StandardCharsets.UTF_8));
                }
            }
            
            // URL 재구성
            StringBuilder encodedUrl = new StringBuilder();
            encodedUrl.append(protocol).append("://").append(host);
            
            if (port != -1) {
                encodedUrl.append(":").append(port);
            }
            
            encodedUrl.append(encodedPath.toString());
            
            if (query != null) {
                encodedUrl.append("?").append(query);
            }
            
            return encodedUrl.toString();
            
        } catch (Exception e) {
            log.warn("URL 인코딩 실패, 원본 URL 사용: {}", e.getMessage());
            return url;
        }
    }

}