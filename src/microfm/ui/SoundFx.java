package microfm.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Efeitos opcionais sintetizados com Java Sound, sem ficheiros de áudio.
 * A criação das amostras e a reprodução decorrem fora da thread do Swing.
 */
public final class SoundFx implements AutoCloseable {
    private static final int SAMPLE_RATE = 22_050;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private final ThreadPoolExecutor audio;
    private volatile boolean enabled;
    private volatile boolean closed;
    private volatile boolean unavailable;
    private volatile long generation;
    private byte[] whistleSamples;
    private byte[] goalSamples;

    public SoundFx() {
        audio = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4), runnable -> {
                Thread thread = new Thread(runnable, "microfm-sound");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());
    }

    /** O som está desligado inicialmente; desligar invalida os efeitos já agendados. */
    public void setEnabled(boolean enabled) {
        if (closed) return;
        this.enabled = enabled;
        if (!enabled) {
            generation++;
            audio.getQueue().clear();
        }
    }

    public boolean enabled() { return enabled && !closed; }

    public void whistle() { enqueue(false); }
    public void goal() { enqueue(true); }

    private void enqueue(boolean goal) {
        if (!enabled || closed || unavailable) return;
        long requestedGeneration = generation;
        audio.execute(() -> {
            if (!canPlay(requestedGeneration)) return;
            byte[] samples;
            if (goal) {
                if (goalSamples == null) goalSamples = synthesizeGoal();
                samples = goalSamples;
            } else {
                if (whistleSamples == null) whistleSamples = synthesizeWhistle();
                samples = whistleSamples;
            }
            play(samples, requestedGeneration);
        });
    }

    private boolean canPlay(long requestedGeneration) {
        return enabled && !closed && generation == requestedGeneration && !Thread.currentThread().isInterrupted();
    }

    private void play(byte[] samples, long requestedGeneration) {
        if (!canPlay(requestedGeneration)) return;
        // A linha pertence apenas ao trabalhador e fecha mesmo se o dispositivo falhar.
        try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
            line.open(FORMAT, 4_096);
            if (!canPlay(requestedGeneration)) return;
            line.start();
            int offset = 0;
            while (offset < samples.length && canPlay(requestedGeneration)) {
                int count = Math.min(1_024, samples.length - offset);
                int written = line.write(samples, offset, count);
                if (written <= 0) break;
                offset += written;
            }
            // Espera curta e interruptível pelo fim dos últimos ~90 ms de áudio.
            long drainDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(350);
            while (canPlay(requestedGeneration) && line.available() < line.getBufferSize()
                    && System.nanoTime() < drainDeadline) {
                try { Thread.sleep(10); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            line.stop();
            line.flush();
        } catch (Exception | LinkageError e) {
            // Algumas máquinas/VM não dispõem de um misturador de áudio. O jogo continua.
            unavailable = true;
            audio.getQueue().clear();
        }
    }

    private static byte[] synthesizeWhistle() {
        int count = (int) (SAMPLE_RATE * .86);
        byte[] samples = new byte[count * 2];
        Random noise = new Random(17);
        double phase = 0;
        for (int i = 0; i < count; i++) {
            double time = i / (double) SAMPLE_RATE;
            double local = time < .31 ? time : time - .40;
            double duration = time < .31 ? .27 : .42;
            double envelope = local < 0 || local > duration ? 0
                : Math.min(1, local / .016) * Math.min(1, (duration - local) / .045);
            double frequency = 2_540 + 70 * Math.sin(2 * Math.PI * 27 * time)
                + 140 * Math.sin(2 * Math.PI * 4.5 * time);
            phase += 2 * Math.PI * frequency / SAMPLE_RATE;
            double tone = .65 * Math.sin(phase) + .17 * Math.sin(phase * 1.013)
                + .05 * (noise.nextDouble() * 2 - 1);
            writeSample(samples, i, tone * envelope * .33);
        }
        return samples;
    }

    private static byte[] synthesizeGoal() {
        int count = (int) (SAMPLE_RATE * 2.4);
        byte[] samples = new byte[count * 2];
        Random noise = new Random(91);
        double lowNoise = 0, midNoise = 0;
        for (int i = 0; i < count; i++) {
            double time = i / (double) SAMPLE_RATE;
            double random = noise.nextDouble() * 2 - 1;
            lowNoise += .10 * (random - lowNoise);
            midNoise += .47 * (random - midNoise);
            double envelope = Math.min(1, time / .17) * Math.pow(Math.max(0, 1 - time / 2.4), .65);
            double voices = .05 * Math.sin(2 * Math.PI * (167 * time + 2.5 * Math.sin(time * 4)))
                + .04 * Math.sin(2 * Math.PI * 223 * time)
                + .04 * Math.sin(2 * Math.PI * 287 * time);
            double roar = lowNoise * 1.05 + (midNoise - lowNoise) * .25 + voices;
            double pulse = .8 + .2 * Math.sin(time * 11);
            writeSample(samples, i, roar * envelope * pulse * .9);
        }
        return samples;
    }

    private static void writeSample(byte[] samples, int index, double value) {
        int sample = (int) (Math.max(-1, Math.min(1, value)) * 32_767);
        samples[index * 2] = (byte) sample;
        samples[index * 2 + 1] = (byte) (sample >> 8);
    }

    @Override public void close() {
        closed = true;
        enabled = false;
        generation++;
        audio.getQueue().clear();
        audio.shutdownNow();
    }
}
