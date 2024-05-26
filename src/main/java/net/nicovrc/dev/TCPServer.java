package net.nicovrc.dev;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class TCPServer extends Thread{

    @Override
    public void run() {

        try {
            final ServerSocket socket = new ServerSocket(22222);
            while (true) {
                final Socket sock = socket.accept();
                System.gc();
                new Thread(() -> {
                    try {
                        final InputStream in = sock.getInputStream();
                        final OutputStream out = sock.getOutputStream();
                        byte[] data = new byte[100000000];

                        int readSize = in.read(data);
                        data = Arrays.copyOf(data, readSize);

                        final byte[] bytes = data;
                        if (bytes.length == 0) {
                            sock.close();
                            return;
                        }



                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            }

        } catch (Exception e){
            e.printStackTrace();
        }

    }
}
