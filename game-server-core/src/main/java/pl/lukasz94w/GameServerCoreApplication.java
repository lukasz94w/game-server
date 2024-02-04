package pl.lukasz94w;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GameServerCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameServerCoreApplication.class, args);
    }

    public static int someTestMethodForJenkinsTesting(int number1, int number2) {
        return number1 + number2;
    }
}