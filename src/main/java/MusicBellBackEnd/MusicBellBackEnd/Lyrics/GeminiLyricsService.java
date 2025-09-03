package MusicBellBackEnd.MusicBellBackEnd.Lyrics;

import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto.LyricsGenerationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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
public class GeminiLyricsService {
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Value("${openai.whisper.api.url:https://api.openai.com/v1/audio/transcriptions}")
    private String whisperApiUrl;
    
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    
    @Value("${gemini.api.url}")
    private String geminiApiUrl;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 음악 파일에서 가사 추출 (Speech-to-Text)
     */
    public String generateLyrics(LyricsGenerationRequest request) {
        log.info("음악 파일에서 가사 추출 시작: musicUrl={}, title={}", 
                request.getMusicUrl(), request.getMusicTitle());
        
        // URL 유효성 검사
        if (request.getMusicUrl() == null || request.getMusicUrl().trim().isEmpty()) {
            log.error("음악 URL이 비어있습니다.");
            return "";
        }
        
        Path audioFile = null;
        Path processedAudio = null;
        
        try {
            // 1. S3에서 음악 파일 다운로드
            log.info("1단계: 음악 파일 다운로드 시작");
            audioFile = downloadAudioFile(request.getMusicUrl());
            
            // 2. 오디오 파일 전처리 및 최적화
            log.info("2단계: 오디오 파일 전처리 시작");
            processedAudio = preprocessAudioFile(audioFile);
            
            // 3. Google Cloud Speech-to-Text API로 음성 인식
            log.info("3단계: Speech-to-Text 처리 시작");
            String extractedText = performSpeechToText(processedAudio);
            
            // 4. 추출된 텍스트 검증 및 반환
            if (extractedText == null || extractedText.trim().isEmpty()) {
                log.warn("Speech-to-Text 결과가 비어있습니다.");
                return "";
            }
            
            log.info("가사 추출 완료: {} 글자", extractedText.length());
            return extractedText;
            
        } catch (Exception e) {
            log.error("가사 추출 중 오류 발생: {}", e.getMessage(), e);
            
            // 단계별 폴백 시도
            return handleGenerationFailure(request, e);
            
        } finally {
            // 임시 파일 정리
            cleanupTempFiles(audioFile, processedAudio);
        }
    }
    
    /**
     * 생성 실패 시 폴백 처리
     */
    private String handleGenerationFailure(LyricsGenerationRequest request, Exception originalError) {
        log.warn("가사 추출 실패: {}", originalError.getMessage());
        
        // Speech-to-Text 실패 시 빈 문자열 반환 (창작하지 않음)
        log.info("Speech-to-Text 실패로 인해 빈 가사를 반환합니다.");
        return "";
    }
    


    /**
     * 가사 번역
     */
    public String translateLyrics(String lyrics, String sourceLanguage, String targetLanguage) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new RuntimeException("Gemini API 키가 설정되지 않았습니다.");
        }
        
        String prompt = buildTranslationPrompt(lyrics, sourceLanguage, targetLanguage);
        
        try {
            String response = callGeminiApi(prompt);
            return extractLyricsFromResponse(response);
        } catch (Exception e) {
            log.error("가사 번역 API 호출 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("가사 번역 API 호출에 실패했습니다: " + e.getMessage());
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
     * 오디오 파일 전처리 (압축 및 최적화)
     */
    private Path preprocessAudioFile(Path audioFile) throws IOException {
        log.info("오디오 파일 전처리 시작: {}", audioFile.toString());
        
        long originalSize = Files.size(audioFile);
        log.info("원본 파일 크기: {} bytes ({} MB)", originalSize, originalSize / (1024 * 1024));
        
        // Google Speech API 제한: 10MB 또는 60초
        long maxSize = 10 * 1024 * 1024; // 10MB 제한
        
        // 파일이 너무 크면 원본 그대로 시도 (샘플링 대신)
        if (originalSize > maxSize) {
            log.warn("오디오 파일이 Speech API 제한(10MB)을 초과합니다. 원본 그대로 시도합니다.");
            // 샘플링 대신 원본 파일 그대로 사용 (API에서 자동으로 처리)
        }
        
        // 파일 형식 확인 및 검증
        String fileName = audioFile.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".mp3") && !fileName.endsWith(".wav") && !fileName.endsWith(".flac")) {
            log.warn("지원하지 않는 오디오 형식: {}. MP3로 가정하고 처리합니다.", fileName);
        }
        
        // MP3 파일 헤더 검증 (메타데이터 제거는 선택적으로)
        if (fileName.endsWith(".mp3")) {
            Path cleanedFile = validateAndCleanMp3File(audioFile);
            if (cleanedFile != audioFile) {
                log.info("MP3 메타데이터 정리 완료");
                return cleanedFile;
            }
        }
        
        return audioFile;
    }
    
    /**
     * MP3 파일 검증 및 정리
     */
    private Path validateAndCleanMp3File(Path mp3File) throws IOException {
        byte[] fileBytes = Files.readAllBytes(mp3File);
        
        // MP3 헤더 확인 (ID3 태그 제거)
        int audioStart = findMp3AudioStart(fileBytes);
        
        if (audioStart > 0) {
            log.info("MP3 메타데이터 제거: {} bytes", audioStart);
            
            // 오디오 데이터만 추출
            byte[] audioOnlyBytes = new byte[fileBytes.length - audioStart];
            System.arraycopy(fileBytes, audioStart, audioOnlyBytes, 0, audioOnlyBytes.length);
            
            // 정리된 파일 생성
            Path cleanedFile = Files.createTempFile("cleaned_audio_", ".mp3");
            Files.write(cleanedFile, audioOnlyBytes);
            
            log.info("MP3 정리 완료: {} bytes -> {} bytes", fileBytes.length, audioOnlyBytes.length);
            return cleanedFile;
        }
        
        return mp3File;
    }
    
    /**
     * MP3 파일에서 실제 오디오 데이터 시작점 찾기
     */
    private int findMp3AudioStart(byte[] fileBytes) {
        // ID3v2 태그 확인
        if (fileBytes.length >= 10 && 
            fileBytes[0] == 'I' && fileBytes[1] == 'D' && fileBytes[2] == '3') {
            
            // ID3v2 태그 크기 계산
            int tagSize = ((fileBytes[6] & 0x7F) << 21) |
                         ((fileBytes[7] & 0x7F) << 14) |
                         ((fileBytes[8] & 0x7F) << 7) |
                         (fileBytes[9] & 0x7F);
            
            return 10 + tagSize; // 헤더(10) + 태그 크기
        }
        
        // MP3 프레임 헤더 찾기 (0xFF 0xFB 또는 0xFF 0xFA)
        for (int i = 0; i < fileBytes.length - 1; i++) {
            if ((fileBytes[i] & 0xFF) == 0xFF && 
                ((fileBytes[i + 1] & 0xF0) == 0xF0)) {
                return i;
            }
        }
        
        return 0; // 메타데이터 없음
    }
    


    /**
     * OpenAI Whisper API 호출
     */
    private String performSpeechToText(Path audioFile) throws IOException {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            log.warn("OpenAI API 키가 설정되지 않았습니다.");
            return "";
        }
        
        log.info("Whisper API 처리 시작: {}", audioFile.toString());
        
        try {
            // 파일 크기 확인
            long fileSize = Files.size(audioFile);
            log.info("처리할 오디오 파일 크기: {} bytes ({} MB)", fileSize, fileSize / (1024 * 1024));
            
            // Whisper API는 25MB까지 지원
            long maxFileSize = 25 * 1024 * 1024; // 25MB
            
            if (fileSize <= maxFileSize) {
                log.info("Whisper API 직접 처리");
                return callWhisperApi(audioFile);
            } else {
                log.warn("파일이 25MB를 초과합니다. 분할 처리가 필요합니다.");
                return performChunkedWhisperProcessing(audioFile);
            }
            
        } catch (Exception e) {
            log.error("Whisper API 호출 중 오류 발생: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * OpenAI Whisper API 직접 호출
     */
    private String callWhisperApi(Path audioFile) throws IOException {
        log.info("Whisper API 호출 시작");
        
        // Multipart 요청 생성
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(openaiApiKey);
        
        // 파일 읽기
        byte[] audioBytes = Files.readAllBytes(audioFile);
        
        // Multipart body 생성
        org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
        
        // 파일 추가
        org.springframework.core.io.ByteArrayResource fileResource = new org.springframework.core.io.ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "audio.mp3";
            }
        };
        body.add("file", fileResource);
        
        // 모델 및 설정
        body.add("model", "whisper-1");
        body.add("language", "ko"); // 한국어 우선
        body.add("response_format", "text");
        body.add("temperature", "0"); // 일관된 결과를 위해
        
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> requestEntity = 
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
                String transcript = response.getBody();
                if (transcript != null && !transcript.trim().isEmpty()) {
                    log.info("Whisper API 성공: {} 글자 추출", transcript.length());
                    return transcript.trim();
                } else {
                    log.warn("Whisper API 응답이 비어있습니다.");
                    return "";
                }
            } else {
                log.error("Whisper API 호출 실패: {}", response.getStatusCode());
                return "";
            }
            
        } catch (Exception e) {
            log.error("Whisper API 호출 실패: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 큰 파일을 위한 분할 처리 (Whisper용)
     */
    private String performChunkedWhisperProcessing(Path audioFile) throws IOException {
        log.info("Whisper 분할 처리 시작");
        
        // 실제로는 FFmpeg 등을 사용해서 시간 단위로 분할해야 함
        // 현재는 간단한 바이트 분할로 처리
        byte[] audioBytes = Files.readAllBytes(audioFile);
        int chunkSize = 20 * 1024 * 1024; // 20MB 청크
        
        StringBuilder fullTranscript = new StringBuilder();
        int chunkCount = (int) Math.ceil((double) audioBytes.length / chunkSize);
        
        log.info("총 {} 개의 청크로 분할하여 처리", chunkCount);
        
        for (int i = 0; i < chunkCount; i++) {
            try {
                int startPos = i * chunkSize;
                int endPos = Math.min(startPos + chunkSize, audioBytes.length);
                
                // MP3 프레임 경계에서 분할
                if (i > 0) {
                    startPos = findNextMp3FrameStart(audioBytes, startPos);
                }
                if (i < chunkCount - 1) {
                    endPos = findNextMp3FrameStart(audioBytes, endPos);
                }
                
                byte[] chunkBytes = Arrays.copyOfRange(audioBytes, startPos, endPos);
                
                // 임시 파일 생성
                Path chunkFile = Files.createTempFile("whisper_chunk_", ".mp3");
                Files.write(chunkFile, chunkBytes);
                
                log.info("청크 {}/{} 처리 중: {} bytes", i + 1, chunkCount, chunkBytes.length);
                
                String chunkResult = callWhisperApi(chunkFile);
                if (chunkResult != null && !chunkResult.trim().isEmpty()) {
                    fullTranscript.append(chunkResult.trim()).append(" ");
                    log.info("청크 {} 처리 완료: {} 글자", i + 1, chunkResult.length());
                }
                
                // 임시 파일 정리
                Files.deleteIfExists(chunkFile);
                
                // API 호출 간격
                if (i < chunkCount - 1) {
                    Thread.sleep(1000);
                }
                
            } catch (Exception e) {
                log.error("청크 {} 처리 중 오류: {}", i + 1, e.getMessage());
            }
        }
        
        String finalResult = fullTranscript.toString().trim();
        log.info("Whisper 분할 처리 완료: 총 {} 글자", finalResult.length());
        
        return finalResult;
    }
    

    
    /**
     * MP3 프레임 시작점 찾기
     */
    private int findNextMp3FrameStart(byte[] bytes, int startPos) {
        for (int i = startPos; i < bytes.length - 1; i++) {
            if ((bytes[i] & 0xFF) == 0xFF && 
                ((bytes[i + 1] & 0xF0) == 0xF0)) {
                return i;
            }
        }
        return startPos; // 찾지 못하면 원래 위치
    }
    

    

    

    

    


    /**
     * Speech-to-Text API 응답에서 텍스트 추출
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromSpeechResponse(Map<String, Object> response) {
        try {
            log.debug("Speech API 전체 응답: {}", response);
            
            // 에러 체크
            if (response.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) response.get("error");
                String errorMessage = (String) error.get("message");
                log.error("Speech API 에러: {}", errorMessage);
                throw new RuntimeException("Speech API 에러: " + errorMessage);
            }
            
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) {
                log.warn("Speech-to-Text 결과가 없습니다. 응답: {}", response);
                return "";
            }
            
            StringBuilder fullText = new StringBuilder();
            
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> result = results.get(i);
                List<Map<String, Object>> alternatives = (List<Map<String, Object>>) result.get("alternatives");
                
                if (alternatives != null && !alternatives.isEmpty()) {
                    for (int j = 0; j < alternatives.size(); j++) {
                        Map<String, Object> alternative = alternatives.get(j);
                        String transcript = (String) alternative.get("transcript");
                        Double confidence = (Double) alternative.get("confidence");
                        
                        if (transcript != null && !transcript.trim().isEmpty()) {
                            log.info("인식 결과 [{}][{}]: {} (신뢰도: {})", i, j, transcript, confidence);
                            
                            // 첫 번째 대안만 사용 (가장 높은 신뢰도)
                            if (j == 0) {
                                fullText.append(transcript.trim()).append("\n");
                            }
                        }
                    }
                }
            }
            
            String finalText = fullText.toString().trim();
            log.info("최종 추출된 텍스트 길이: {} 글자", finalText.length());
            
            return finalText;
            
        } catch (Exception e) {
            log.error("Speech-to-Text 응답 파싱 오류: {}", e.getMessage(), e);
            throw new RuntimeException("음성 인식 결과 파싱에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * Gemini를 사용하여 추출된 텍스트를 가사 형태로 정리
     */
    public String formatLyricsWithGemini(String extractedText, LyricsGenerationRequest request) {
        // Gemini API는 선택적 사용 (가사 정리용)
        
        String prompt = buildLyricsFormattingPrompt(extractedText, request);
        
        try {
            String response = callGeminiApi(prompt);
            return extractLyricsFromResponse(response);
        } catch (Exception e) {
            log.error("Gemini를 통한 가사 정리 실패, 원본 텍스트 반환: {}", e.getMessage());
            return extractedText; // 실패시 원본 텍스트 반환
        }
    }

    /**
     * 가사 정리용 프롬프트 생성
     */
    private String buildLyricsFormattingPrompt(String extractedText, LyricsGenerationRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음은 음악에서 추출된 텍스트입니다. 이를 가사 형태로 정리해주세요:\n\n");
        prompt.append("음악 제목: ").append(request.getMusicTitle()).append("\n");
        prompt.append("아티스트: ").append(request.getArtistName()).append("\n");
        prompt.append("추출된 텍스트:\n").append(extractedText).append("\n\n");
        prompt.append("요구사항:\n");
        prompt.append("1. 가사를 의미 있는 줄로 나누어 주세요\n");
        prompt.append("2. 반복되는 부분(후렴구)을 식별해주세요\n");
        prompt.append("3. 불필요한 소음이나 배경음은 제거해주세요\n");
        prompt.append("4. 자연스러운 가사 형태로 정리해주세요\n");
        prompt.append("5. 가사만 출력하고, 다른 설명은 포함하지 마세요\n");
        
        return prompt.toString();
    }

    private String buildTranslationPrompt(String lyrics, String sourceLanguage, String targetLanguage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음 가사를 ").append(getLanguageName(sourceLanguage))
              .append("에서 ").append(getLanguageName(targetLanguage))
              .append("로 번역해주세요:\n\n");
        prompt.append(lyrics).append("\n\n");
        prompt.append("요구사항:\n");
        prompt.append("1. 원본의 의미와 감정을 최대한 보존해주세요\n");
        prompt.append("2. 자연스러운 ").append(getLanguageName(targetLanguage)).append(" 표현을 사용해주세요\n");
        prompt.append("3. 음악 가사의 운율과 리듬감을 고려해주세요\n");
        prompt.append("4. 번역된 가사만 출력하고, 다른 설명은 포함하지 마세요\n");
        
        return prompt.toString();
    }

    private String callGeminiApi(String prompt) {
        String url = geminiApiUrl + "?key=" + geminiApiKey;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, String> part = new HashMap<>();
        part.put("text", prompt);
        
        content.put("parts", List.of(part));
        requestBody.put("contents", List.of(content));
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return extractTextFromGeminiResponse(response.getBody());
        } else {
            throw new RuntimeException("Gemini API 호출 실패: " + response.getStatusCode());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromGeminiResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
            throw new RuntimeException("Gemini API 응답에서 텍스트를 추출할 수 없습니다.");
        } catch (Exception e) {
            log.error("Gemini API 응답 파싱 오류: {}", e.getMessage(), e);
            throw new RuntimeException("API 응답 파싱에 실패했습니다: " + e.getMessage());
        }
    }

    private String extractLyricsFromResponse(String response) {
        // 응답에서 불필요한 부분 제거 및 정리
        return response.trim()
                .replaceAll("```[a-zA-Z]*\n?", "") // 코드 블록 마커 제거
                .replaceAll("^[가사|Lyrics|歌詞][:：]?\\s*", "") // 제목 부분 제거
                .trim();
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

    private String getLanguageName(String languageCode) {
        return switch (languageCode.toUpperCase()) {
            case "KO" -> "한국어";
            case "EN" -> "영어";
            case "JP" -> "일본어";
            case "CN" -> "중국어";
            case "ES" -> "스페인어";
            case "FR" -> "프랑스어";
            default -> "한국어";
        };
    }
}