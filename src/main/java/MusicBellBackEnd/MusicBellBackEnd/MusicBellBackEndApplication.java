package MusicBellBackEnd.MusicBellBackEnd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class MusicBellBackEndApplication {

	public static void main(String[] args) {
		SpringApplication.run(MusicBellBackEndApplication.class, args);
		log.info("안녕하세요 MusicBell 입니다!");
	}

}
