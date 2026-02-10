package com.k4ln.debug4j.core.attach.jvm.proxy;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class Debug4jHttpProxy {

    /**
     * 代理服务端口号
     */
    private static final int PORT = 7980;

    /**
     * 请求线程池
     */
    private static final ExecutorService pool = Executors.newFixedThreadPool(200);

    /**
     * 连接超时
     */
    public static final int CONNECT_TIMEOUT = 10000;

    /**
     * 读取超时
     */
    public static final int SO_TIMEOUT = 30000;

    /**
     * 服务线程
     */
    public static Thread proxyServerThread = buildProxyServerThread();

    private static Thread buildProxyServerThread() {
        return new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                log.info("debug4j http(s) proxy started on port {}", PORT);
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        pool.submit(() -> handle(clientSocket));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 启动代理服务
     */
    public static void start() {
        if (!proxyServerThread.isAlive()) {
            proxyServerThread.start();
        }
    }

    /**
     * 停止代理服务
     */
    public static void stop() {
        if (proxyServerThread.isAlive()) {
            proxyServerThread.interrupt();
            proxyServerThread = buildProxyServerThread();
        }
    }

    /**
     * 处理请求
     *
     * @param client
     */
    private static void handle(Socket client) {
        try (client) {
            InputStream clientIn = client.getInputStream();
            OutputStream clientOut = client.getOutputStream();
            while (true) {
                byte[] headerBytes = readHeader(clientIn);
                if (headerBytes == null) break;
                String header = (new String(headerBytes)).trim();
                log.debug("\n{}", header);
                String[] lines = header.split("\r\n");
                String[] first = lines[0].split(" ");
                String method = first[0];
                String uri = first[1];
                if ("CONNECT".equalsIgnoreCase(method)) {
                    handleHttps(uri, clientIn, clientOut);
                    return;
                }
                URL url = new URL(uri);
                int port = url.getPort() == -1 ? 80 : url.getPort();
                byte[] body = readBody(clientIn, header);
                try (Socket server = new Socket()) {
                    server.connect(new InetSocketAddress(url.getHost(), port), CONNECT_TIMEOUT);
                    server.setSoTimeout(SO_TIMEOUT);
                    OutputStream serverOut = server.getOutputStream();
                    InputStream serverIn = server.getInputStream();
                    serverOut.write(rewriteHeader(header, url).getBytes());
                    if (body != null) serverOut.write(body);
                    serverOut.flush();
                    pipeResponse(serverIn, clientOut);
                } catch (IOException e) {
                    log.warn("{} http proxy error:{}", uri, e.getMessage());
                }
                if (!isKeepAlive(header)) break;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 处理https请求
     *
     * @param target
     * @param clientIn
     * @param clientOut
     * @throws Exception
     */
    private static void handleHttps(String target, InputStream clientIn, OutputStream clientOut) {
        String[] hp = target.split(":");
        try (Socket server = new Socket()) {
            server.connect(new InetSocketAddress(hp[0], Integer.parseInt(hp[1])), CONNECT_TIMEOUT);
            server.setSoTimeout(SO_TIMEOUT);
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
            clientOut.flush();
            Thread t1 = new Thread(() -> forward(clientIn, server));
            Thread t2 = new Thread(() -> forward(server, clientOut));
            t1.start();
            t2.start();
            t1.join();
            t2.join();
        } catch (Exception e) {
            log.warn("{} https proxy error:{}", target, e.getMessage());
        }
    }

    /**
     * 转发（发送）数据
     *
     * @param in
     * @param outSock
     */
    private static void forward(InputStream in, Socket outSock) {
        try {
            OutputStream out = outSock.getOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * 转发（回传）数据
     *
     * @param inSock
     * @param out
     */
    private static void forward(Socket inSock, OutputStream out) {
        try {
            InputStream in = inSock.getInputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * 读取请求头
     *
     * @param in
     * @return
     * @throws IOException
     */
    private static byte[] readHeader(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            baos.write(b);
            int n = baos.size();
            if (n >= 4) {
                byte[] t = baos.toByteArray();
                if (t[n - 4] == '\r' && t[n - 3] == '\n' && t[n - 2] == '\r' && t[n - 1] == '\n') {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * 读取请求体
     *
     * @param in
     * @param header
     * @return
     * @throws IOException
     */
    private static byte[] readBody(InputStream in, String header) throws IOException {
        int len = getContentLength(header);
        if (len <= 0) return null;
        byte[] body = new byte[len];
        int read = 0;
        while (read < len) {
            int r = in.read(body, read, len - read);
            if (r == -1) break;
            read += r;
        }
        return body;
    }

    /**
     * 响应管道
     *
     * @param serverIn
     * @param clientOut
     * @throws IOException
     */
    private static void pipeResponse(InputStream serverIn, OutputStream clientOut) throws IOException {
        byte[] headerBytes = readHeader(serverIn);
        if (headerBytes == null) return;
        clientOut.write(headerBytes);
        clientOut.flush();
        String header = new String(headerBytes).toLowerCase();
        if (header.contains("transfer-encoding: chunked")) {
            pipeChunked(serverIn, clientOut);
        } else {
            int contentLength = getContentLength(header);
            if (contentLength >= 0) {
                copyFixedLength(serverIn, clientOut, contentLength);
            } else {
                byte[] buf = new byte[8192];
                int read;
                while ((read = serverIn.read(buf)) != -1) {
                    clientOut.write(buf, 0, read);
                }
            }
        }
        clientOut.flush();
    }

    /**
     * 读取数据流
     *
     * @param in
     * @param out
     * @throws IOException
     */
    private static void pipeChunked(InputStream in, OutputStream out) throws IOException {
        BufferedInputStream bin = new BufferedInputStream(in);
        while (true) {
            String line = readLine(bin);
            if (line == null) break;
            out.write((line + "\r\n").getBytes());
            int size = Integer.parseInt(line.trim(), 16);
            if (size == 0) {
                String trailer;
                while ((trailer = readLine(bin)) != null && !trailer.isEmpty()) {
                    out.write((trailer + "\r\n").getBytes());
                }
                out.write("\r\n".getBytes());
                break;
            }
            copyFixedLength(bin, out, size);
            readLine(bin);
            out.write("\r\n".getBytes());
        }
    }

    /**
     * 读取一行
     *
     * @param in
     * @return
     * @throws IOException
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int prev = -1;
        int curr;
        while ((curr = in.read()) != -1) {
            if (prev == '\r' && curr == '\n') {
                baos.write('\r');
                return baos.toString().trim();
            }
            if (prev != -1) baos.write(prev);
            prev = curr;
        }
        if (prev != -1) baos.write(prev);
        return baos.size() > 0 ? baos.toString().trim() : null;
    }

    /**
     * 读取固定长度数据
     *
     * @param in
     * @param out
     * @param length
     * @throws IOException
     */
    private static void copyFixedLength(InputStream in, OutputStream out, int length) throws IOException {
        byte[] buf = new byte[8192];
        int total = 0;
        while (total < length) {
            int toRead = Math.min(buf.length, length - total);
            int read = in.read(buf, 0, toRead);
            if (read == -1) break;
            out.write(buf, 0, read);
            total += read;
        }
    }

    /**
     * 获取请求体长度
     *
     * @param header
     * @return
     */
    private static int getContentLength(String header) {
        for (String line : header.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length")) {
                return Integer.parseInt(line.split(":")[1].trim());
            }
        }
        return 0;
    }

    /**
     * 是否保持长连接
     *
     * @param header
     * @return
     */
    private static boolean isKeepAlive(String header) {
        for (String line : header.split("\r\n")) {
            if (line.toLowerCase().startsWith("connection")) {
                return !line.toLowerCase().contains("close");
            }
        }
        return true;
    }

    /**
     * 重写请求头
     *
     * @param header
     * @param url
     * @return
     */
    private static String rewriteHeader(String header, URL url) {
        StringBuilder sb = new StringBuilder();
        String[] lines = header.split("\r\n");
        sb.append(lines[0].replace(url.toString(), url.getFile())).append("\r\n");
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].toLowerCase().startsWith("proxy-connection")) {
                sb.append(lines[i]).append("\r\n");
            }
        }
        sb.append("\r\n");
        return sb.toString();
    }
}

