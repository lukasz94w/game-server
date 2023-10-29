package pl.lukasz94w;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    public static int someTestMethodForJenkinsTesting(int number1, int number2) {
        return number1 + number2;
    }
}