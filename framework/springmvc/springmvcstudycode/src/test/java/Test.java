import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class Test {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080);
        Socket accept = serverSocket.accept();
        System.out.println(accept);
        InputStream inputStream = accept.getInputStream();
        byte[] bytes = inputStream.readAllBytes();
        System.out.println(Arrays.toString(bytes));

        accept.close();
        serverSocket.close();
    }
}
