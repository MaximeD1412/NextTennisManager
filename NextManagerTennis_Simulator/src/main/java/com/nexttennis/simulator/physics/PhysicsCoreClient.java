package com.nexttennis.simulator.physics;

import com.nexttennis.simulator.proto.PhysicsBatchRequest;
import com.nexttennis.simulator.proto.PhysicsBatchResponse;
import com.nexttennis.simulator.proto.PhysicsEnvironment;
import com.nexttennis.simulator.proto.PhysicsSimulationResult;
import com.nexttennis.simulator.proto.ShotSpec;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the physics-core Rust sidecar process lifecycle and exposes simulateBatch().
 *
 * Transport: length-delimited Protobuf over stdin/stdout (varint prefix, same framing as
 * the Rust sidecar in physics-core/src/bin/sidecar.rs).
 */
@Component
public class PhysicsCoreClient {

    private final String binaryPath;

    private volatile Process process;
    private volatile OutputStream stdin;
    private volatile InputStream stdout;

    public PhysicsCoreClient(@Value("${physics-core.binary.path}") String binaryPath) {
        this.binaryPath = binaryPath;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        launchProcess();
        startWatchdog();
    }

    @PreDestroy
    public void stop() {
        if (process != null) {
            process.destroyForcibly();
        }
    }

    /**
     * Sends a batch of shots to the Rust sidecar and returns one result per shot, in order.
     * If the sidecar has died since the last call it is restarted before sending.
     */
    public synchronized List<PhysicsSimulationResult> simulateBatch(
            PhysicsEnvironment env, List<ShotSpec> shots) throws IOException {

        if (process == null || !process.isAlive()) {
            launchProcess();
        }

        PhysicsBatchRequest request = PhysicsBatchRequest.newBuilder()
                .setEnv(env)
                .addAllShots(shots)
                .setSeed(ThreadLocalRandom.current().nextLong())
                .build();

        byte[] payload = request.toByteArray();
        writeVarint(stdin, payload.length);
        stdin.write(payload);
        stdin.flush();

        long len = readVarint(stdout);
        byte[] buf = stdout.readNBytes((int) len);
        return PhysicsBatchResponse.parseFrom(buf).getResultsList();
    }

    private synchronized void launchProcess() throws IOException {
        process = new ProcessBuilder(binaryPath)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        stdin = new BufferedOutputStream(process.getOutputStream());
        stdout = process.getInputStream();
    }

    private void startWatchdog() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (process != null && !process.isAlive()) {
                    try {
                        launchProcess();
                    } catch (IOException ignored) {
                    }
                }
            }
        }, "physics-core-watchdog");
        t.setDaemon(true);
        t.start();
    }

    private static void writeVarint(OutputStream out, long value) throws IOException {
        do {
            int b = (int) (value & 0x7F);
            value >>>= 7;
            if (value != 0) b |= 0x80;
            out.write(b);
        } while (value != 0);
    }

    private static long readVarint(InputStream in) throws IOException {
        long result = 0;
        int shift = 0;
        int b;
        do {
            b = in.read();
            if (b < 0) throw new EOFException("physics-core sidecar closed unexpectedly");
            result |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }
}
