package microfm.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mistura o som das bancadas e os efeitos sintetizados numa linha Java Sound.
 * A síntese e a reprodução decorrem fora da thread do Swing.
 */
public final class SoundFx implements AutoCloseable {
    private static final int SAMPLE_RATE = 22_050;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private static final int FADE_FRAMES = SAMPLE_RATE / 50;

    private enum Effect {
        WHISTLE(true, 1), PERIOD_WHISTLE(true, 2), FINAL_WHISTLE(true, 3),
        RESTART(false, 1), CHANCE(false, 2), RELIEF(false, 2), PROTEST(false, 2), GOAL(false, 3);

        final boolean referee;
        final int priority;

        Effect(boolean referee, int priority) {
            this.referee = referee;
            this.priority = priority;
        }
    }

    public enum Crowd {
        RESTART(Effect.RESTART), CHANCE(Effect.CHANCE), RELIEF(Effect.RELIEF), PROTEST(Effect.PROTEST);

        private final Effect effect;

        Crowd(Effect effect) { this.effect = effect; }
    }

    private static final class Playback {
        final AtomicReference<Effect> referee = new AtomicReference<>();
        final AtomicReference<Effect> crowd = new AtomicReference<>();

        void enqueue(Effect effect) {
            AtomicReference<Effect> channel = effect.referee ? referee : crowd;
            channel.accumulateAndGet(effect, (pending, next) ->
                pending == null || next.priority >= pending.priority ? next : pending);
        }
    }

    private static final class EffectTrack {
        private Effect current;
        private byte[] samples, previousSamples;
        private int offset, previousOffset, fade;

        void start(Effect effect, byte[] clip) {
            if (current != null && current.priority > effect.priority) return;
            previousSamples = samples;
            previousOffset = offset;
            fade = previousSamples == null ? 0 : FADE_FRAMES;
            current = effect;
            samples = clip;
            offset = 0;
        }

        int nextSample() {
            int sample = 0;
            if (samples != null) {
                sample = readSample(samples, offset);
                offset += 2;
                if (offset == samples.length) {
                    current = null;
                    samples = null;
                }
            }
            if (fade > 0) {
                int previous = 0;
                if (previousOffset < previousSamples.length) {
                    previous = readSample(previousSamples, previousOffset);
                    previousOffset += 2;
                }
                sample += (previous - sample) * fade / FADE_FRAMES;
                if (--fade == 0) previousSamples = null;
            }
            return sample;
        }
    }

    private final ThreadPoolExecutor audio;
    private volatile Playback playback;
    private volatile double excitement;
    private boolean closed;

    public SoundFx() {
        audio = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "microfm-sound");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());
    }

    public synchronized void setEnabled(boolean enabled) {
        if (closed || enabled == (playback != null)) return;
        if (!enabled) {
            playback = null;
            audio.getQueue().clear();
            return;
        }
        Playback next = new Playback();
        playback = next;
        audio.execute(() -> play(next));
    }

    public void whistle() { enqueue(Effect.WHISTLE); }
    public void periodWhistle() { enqueue(Effect.PERIOD_WHISTLE); }
    public void finalWhistle() { enqueue(Effect.FINAL_WHISTLE); }
    public void crowd(Crowd reaction) { enqueue(reaction.effect); }
    public void goal() { enqueue(Effect.GOAL); }
    public void setExcitement(double level) { excitement = Math.max(0, Math.min(1, level)); }

    private void enqueue(Effect effect) {
        Playback current = playback;
        if (current != null) current.enqueue(effect);
    }

    private static byte[] synthesizeEffect(Effect effect) {
        return switch (effect) {
            case WHISTLE -> synthesizeWhistle(.21);
            case PERIOD_WHISTLE -> synthesizeWhistle(.18, .31);
            case FINAL_WHISTLE -> synthesizeWhistle(.17, .17, .45);
            case GOAL -> synthesizeGoal();
            case CHANCE -> synthesizeReaction(1.30, .38, .86, 169, 46, 2);
            case RELIEF -> synthesizeReaction(1.16, .08, .74, 224, -64, 3);
            case PROTEST -> synthesizeReaction(1.46, .09, .72, 153, 15, 4);
            case RESTART -> synthesizeReaction(.76, .14, .34, 174, 8, 5);
        };
    }

    private void play(Playback active) {
        if (playback != active) return;
        try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
            line.open(FORMAT, 2_048);
            byte[] crowd = StadiumAudio.AMBIENCE;
            if (playback != active) return;

            byte[] block = new byte[1_024];
            EffectTrack referee = new EffectTrack();
            EffectTrack reaction = new EffectTrack();
            int crowdOffset = 0;
            double crowdGain = 1;
            line.start();
            while (playback == active && !Thread.currentThread().isInterrupted()) {
                if (line.available() < block.length) {
                    Thread.sleep(5);
                    continue;
                }
                Effect whistle = active.referee.getAndSet(null);
                if (whistle != null) referee.start(whistle, StadiumAudio.CLIPS[whistle.ordinal()]);
                Effect event = active.crowd.getAndSet(null);
                if (event != null) reaction.start(event, StadiumAudio.CLIPS[event.ordinal()]);
                double targetGain = 1 + excitement * .8;
                for (int offset = 0; offset < block.length; offset += 2) {
                    crowdGain += (targetGain - crowdGain) * .0003;
                    int ambient = (int) (readSample(crowd, crowdOffset) * crowdGain);
                    int sample = ambient + referee.nextSample() + reaction.nextSample();
                    crowdOffset += 2;
                    if (crowdOffset == crowd.length) crowdOffset = 0;
                    writePcmSample(block, offset, sample);
                }
                if (playback != active || line.write(block, 0, block.length) <= 0) break;
            }
            line.stop();
            line.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception | LinkageError e) {
            // Algumas máquinas/VM não dispõem de um misturador de áudio. O jogo continua.
        }
    }

    private static final class StadiumAudio {
        private static final byte[] AMBIENCE = synthesizeCrowd();
        private static final byte[][] CLIPS = Arrays.stream(Effect.values()).map(SoundFx::synthesizeEffect).toArray(byte[][]::new);
    }

    private static byte[] synthesizeCrowd() {
        int loopLength = 20 * SAMPLE_RATE;
        int overlap = SAMPLE_RATE;
        double[] crowd = new double[loopLength + overlap];
        Random random = new Random(83);
        double low = 0, middle = 0, high = 0;
        for (int i = 0; i < crowd.length; i++) {
            double noise = random.nextDouble() * 2 - 1;
            low += .026 * (noise - low);
            middle += .14 * (noise - middle);
            high += .46 * (noise - high);
            double time = i / (double) SAMPLE_RATE;
            double swell = .94 + .16 * Math.sin(time * 1.7) + .1 * Math.sin(time * .62 + 2);
            crowd[i] = swell * (.27 * low + .38 * (middle - low) + .14 * (high - middle));
        }

        // Sobrepõe vozes indistintas com alturas, sílabas e ritmos diferentes.
        for (int voice = 0; voice < 14; voice++) {
            double pitch = 95 + 160 * random.nextDouble();
            double rhythm = 10 + 19 * random.nextDouble();
            double start = 2 * Math.PI * random.nextDouble();
            double phase = start;
            for (int i = 0; i < crowd.length; i++) {
                double time = i / (double) SAMPLE_RATE;
                phase += 2 * Math.PI * (pitch + 13 * Math.sin(time * 5 + start)) / SAMPLE_RATE;
                double syllable = Math.max(0, Math.sin(time * rhythm + start));
                double phrase = .55 + .45 * Math.sin(time * (1.6 + voice * .15) + start);
                double tone = .44 * Math.sin(phase) + .31 * Math.sin(phase * 2) + .25 * Math.sin(phase * 3);
                crowd[i] += .041 * syllable * syllable * phrase * tone;
            }
        }

        // Cruza um segundo das duas pontas para a repetição não ter um corte audível.
        byte[] samples = new byte[loopLength * 2];
        for (int i = 0; i < overlap; i++) {
            double blend = i / (double) overlap;
            crowd[i] = blend * crowd[i] + (1 - blend) * crowd[i + loopLength];
        }
        for (int i = 0; i < loopLength; i++) writeSample(samples, i, crowd[i]);
        return samples;
    }

    private static byte[] synthesizeWhistle(double... durations) {
        int gap = (int) (SAMPLE_RATE * .12);
        int count = gap * (durations.length - 1);
        for (double duration : durations) count += (int) (SAMPLE_RATE * duration);
        byte[] samples = new byte[count * 2];
        Random noise = new Random(17);
        int offset = 0;
        for (double duration : durations) {
            int frames = (int) (SAMPLE_RATE * duration);
            double phase = 0;
            for (int i = 0; i < frames; i++) {
                double time = i / (double) SAMPLE_RATE;
                double envelope = Math.min(1, time / .012) * Math.min(1, (duration - time) / .038);
                double frequency = 2_540 + 90 * Math.sin(2 * Math.PI * 27 * time)
                    + 140 * Math.sin(2 * Math.PI * 4.5 * time);
                phase += 2 * Math.PI * frequency / SAMPLE_RATE;
                double tone = .65 * Math.sin(phase) + .19 * Math.sin(phase * 1.018)
                    + .06 * Math.sin(phase * 2) + .05 * (noise.nextDouble() * 2 - 1);
                writeSample(samples, offset + i, tone * envelope * .36);
            }
            offset += frames + gap;
        }
        return samples;
    }

    private static byte[] synthesizeReaction(double duration, double attack, double gain,
        double pitch, double pitchChange, long seed) {
        int count = (int) (SAMPLE_RATE * duration);
        byte[] samples = new byte[count * 2];
        Random noise = new Random(seed);
        double low = 0, mid = 0, phase = 0;
        for (int i = 0; i < count; i++) {
            double time = i / (double) SAMPLE_RATE;
            double progress = time / duration;
            double random = noise.nextDouble() * 2 - 1;
            low += .075 * (random - low);
            mid += .34 * (random - mid);
            phase += 2 * Math.PI * (pitch + pitchChange * progress) / SAMPLE_RATE;
            double voices = .07 * Math.sin(phase + 3 * Math.sin(time * 9))
                + .05 * Math.sin(phase * 1.21 + 2 * Math.sin(time * 12))
                + .04 * Math.sin(phase * 1.49 + Math.sin(time * 16));
            double envelope = Math.min(1, time / attack) * Math.pow(1 - progress, .9);
            double reaction = .75 * low + .21 * (mid - low) + voices;
            writeSample(samples, i, reaction * envelope * gain);
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
        writePcmSample(samples, index * 2, (int) (value * Short.MAX_VALUE));
    }

    private static int readSample(byte[] samples, int offset) {
        return (short) ((samples[offset] & 0xff) | (samples[offset + 1] << 8));
    }

    private static void writePcmSample(byte[] samples, int offset, int value) {
        int sample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        samples[offset] = (byte) sample;
        samples[offset + 1] = (byte) (sample >> 8);
    }

    @Override public synchronized void close() {
        closed = true;
        playback = null;
        audio.shutdownNow();
    }
}
