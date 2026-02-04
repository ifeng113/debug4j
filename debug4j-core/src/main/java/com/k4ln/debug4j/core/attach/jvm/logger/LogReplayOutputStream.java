package com.k4ln.debug4j.core.attach.jvm.logger;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Slf4j
public class LogReplayOutputStream extends OutputStream {

    private final OutputStream original;

    private final Consumer<String> lineProcessor;

    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

    public LogReplayOutputStream(OutputStream original, Consumer<String> lineProcessor) {
        this.original = original;
        this.lineProcessor = lineProcessor;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        original.write(b);
        lineBuffer.write(b);
        lineProcessor();
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        original.write(b, off, len);
        lineBuffer.write(b, off, len);
        lineProcessor();
    }

    @Override
    public synchronized void flush() throws IOException {
        if (lineBuffer.size() > 0) lineProcessor();
        original.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        flush();
        original.close();
    }

    private void lineProcessor() {
        lineProcessor.accept(lineBuffer.toString(StandardCharsets.UTF_8));
        lineBuffer.reset();
    }
}
