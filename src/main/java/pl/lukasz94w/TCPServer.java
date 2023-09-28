package pl.lukasz94w;

import org.apache.log4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;

public class TCPServer {

    private static final Logger logger = Logger.getLogger(TCPServer.class);
    private static final Clock clock = Clock.systemDefaultZone();

    public TCPServer(int listeningPort) {
        try (ServerSocket serverSocket = new ServerSocket(listeningPort)) {
            Socket socket = serverSocket.accept();
            logger.info("Client connected at: " + clock.instant());
            System.out.println("Client connected at: " + clock.instant());
            BufferedReader br = new BufferedReader(new InputStreamReader((socket.getInputStream())));

            while (true) {
                String s = br.readLine();
                if (s != null) {
                    System.out.println(s);
                }
            }

        } catch (IOException e) {
            logger.info("Failed to open server socket ");
        }
    }

}
