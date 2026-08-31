package com.example.gsimanager;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalServer {

    public interface Listener {
        void onRequest(String name, long position, long total);
    }

    private ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running;
    private final ContentResolver resolver;
    private final Uri uri;
    private final long size;
    private final String name;
    private Listener listener;

    public LocalServer(ContentResolver resolver, Uri uri, long size, String name) {
        this.resolver = resolver;
        this.uri = uri;
        this.size = size;
        this.name = name;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        running = true;
        new Thread(this::acceptLoop, "gsi-local-server").start();
    }

    public int getPort() {
        return serverSocket == null ? 0 : serverSocket.getLocalPort();
    }

    public String getUrl() {
        return "http://127.0.0.1:" + getPort() + "/" + name;
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                pool.execute(() -> handle(socket));
            } catch (IOException e) {
                if (running) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()) {
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return;
            }
            String method = parts[0];
            boolean isGet = method.equals("GET");
            boolean isHead = method.equals("HEAD");

            long rangeStart = 0;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                if (line.regionMatches(true, 0, "Range:", 0, 6)) {
                    String range = line.substring(6).trim();
                    if (range.startsWith("bytes=")) {
                        String first = range.substring(6).split("-")[0].trim();
                        try {
                            rangeStart = Long.parseLong(first);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            if (!isGet && !isHead) {
                writeResponse(out, 405, "Method Not Allowed", null, -1, false);
                return;
            }

            long start = Math.min(rangeStart, Math.max(0, size - 1));
            boolean partial = start > 0;
            String contentRange = partial
                    ? "bytes " + start + "-" + (size - 1) + "/" + size
                    : "bytes 0-" + (size - 1) + "/" + size;
            int status = partial ? 206 : 200;
            String reason = partial ? "Partial Content" : "OK";
            writeResponse(out, status, reason, contentRange, size - start, isHead);
            if (isHead) {
                return;
            }
            streamBody(in, out, start);
        } catch (Exception ignored) {
        }
    }

    private void streamBody(InputStream in, OutputStream out, long start) throws IOException {
        long skipped = 0;
        while (skipped < start) {
            long sk = in.skip(start - skipped);
            if (sk <= 0) {
                int b = in.read();
                if (b < 0) {
                    return;
                }
                skipped += 1;
            } else {
                skipped += sk;
            }
        }
        byte[] buf = new byte[64 * 1024];
        long pos = start;
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            pos += n;
            if (listener != null) {
                listener.onRequest(name, pos, size);
            }
        }
    }

    private void writeResponse(OutputStream out, int status, String reason, String contentRange,
                               long contentLength, boolean head) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        sb.append("Content-Type: application/octet-stream\r\n");
        sb.append("Accept-Ranges: bytes\r\n");
        if (contentRange != null) {
            sb.append("Content-Range: ").append(contentRange).append("\r\n");
        }
        if (contentLength >= 0) {
            sb.append("Content-Length: ").append(contentLength).append("\r\n");
        }
        sb.append("Connection: close\r\n");
        sb.append("\r\n");
        out.write(sb.toString().getBytes("UTF-8"));
        out.flush();
        if (head) {
            return;
        }
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                continue;
            }
            if (b == '\n') {
                break;
            }
            sb.append((char) b);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
