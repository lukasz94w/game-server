package pl.lukasz94w;

public class Main {


    public static void main(String[] args) {
        TCPServer server = new TCPServer(9999);
    }

    public static int someTestMethodForJenkinsTesting(int number1, int number2) {
        return number1 + number2;
    }
}