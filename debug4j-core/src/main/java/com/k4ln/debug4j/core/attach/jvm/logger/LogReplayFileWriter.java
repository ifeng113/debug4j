package com.k4ln.debug4j.core.attach.jvm.logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class LogReplayFileWriter {

    public static final int CAPACITY = 8 * 1024;

    private final FileChannel channel;

    private final CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();

    private ByteBuffer buffer;

    public LogReplayFileWriter(Path path) throws IOException {
        channel = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
        buffer = ByteBuffer.allocateDirect(CAPACITY);
    }

    public void writeLines(List<String> lines) throws IOException {
        int est = lines.stream().mapToInt(String::length).sum();
        CharBuffer cb = CharBuffer.allocate(est);
        for (String s : lines) cb.put(s);
        cb.flip();
        ByteBuffer bb = encoder.encode(cb);
        buffer = ensureCapacity(buffer, bb.remaining());
        buffer.put(bb);
        flush();
    }

    private ByteBuffer ensureCapacity(ByteBuffer buffer, int required) {
        if (buffer.remaining() < required) {
            int newCapacity = Math.max(buffer.capacity() * 2, buffer.position() + required);
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity);
            buffer.flip();
            newBuffer.put(buffer);
            return newBuffer;
        }
        return buffer;
    }

    public void flush() throws IOException {
        buffer.flip();
        while (buffer.hasRemaining()) channel.write(buffer);
        buffer.clear();
    }

    public void close() throws IOException {
        flush();
        channel.close();
    }
}


