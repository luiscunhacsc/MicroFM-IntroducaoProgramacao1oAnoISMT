import microfm.match.MatchCanvas;
import microfm.match.MatchEngine;
import microfm.model.Career;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Locale;
import javax.swing.SwingUtilities;

/**
 * Medição informativa, sem um limite de aprovação dependente do computador.
 * Desenha os pixels reais no EDT: não reduz a imagem nem simula os desenhos.
 * Compilar juntamente com src; executar: java -cp build/classes BenchmarkRender
 */
public final class BenchmarkRender {
    private static final int WARMUP = 75;
    private static final int SAMPLES = 150;
    private static final double FRAME = 1.0 / 60;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.out.printf("Java %s / %s / %s%n", System.getProperty("java.version"),
            System.getProperty("os.name"), System.getProperty("os.arch"));
        System.out.println("Java2D em BufferedImage, no EDT; cenário já em cache, antialiasing ativo.");
        System.out.println("Referência: 60 Hz = 16,67 ms/quadro; uma medição não garante a cadência do ecrã.");
        SwingUtilities.invokeAndWait(() -> {
            measure(900, 500, 1);
            measure(1100, 600, 1);
            measure(1100, 600, 8);
            measureCelebration(1100, 600);
        });
    }

    private static void measure(int width, int height, int speed) {
        MatchEngine engine = new MatchEngine(Career.create("Benchmark", 0, 20261126), 817);
        MatchCanvas canvas = new MatchCanvas(engine);
        canvas.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // Aquece o JIT e cria a imagem estática antes de medir.
        for (int frame = 0; frame < WARMUP; frame++) advanceAndPaint(engine, canvas, image, speed);
        long[] nanos = new long[SAMPLES];
        for (int frame = 0; frame < SAMPLES; frame++) {
            long before = System.nanoTime();
            advanceAndPaint(engine, canvas, image, speed);
            nanos[frame] = System.nanoTime() - before;
        }
        report(width + "×" + height + " / simulação " + speed + "× + pintura", nanos);
        canvas.dispose(); image.flush();
    }

    private static void advanceAndPaint(MatchEngine engine, MatchCanvas canvas, BufferedImage image, int speed) {
        engine.update(FRAME * speed);
        if (engine.halftime()) engine.resumeHalf();
        canvas.updateVisuals(FRAME);
        paint(canvas, image);
    }

    private static void measureCelebration(int width, int height) {
        MatchEngine engine = new MatchEngine(Career.create("Benchmark", 0, 7), 0);
        MatchCanvas canvas = new MatchCanvas(engine);
        canvas.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        paint(canvas, image);
        int attempts = 0;
        while (engine.goalSerial() == 0 && !engine.finished() && attempts++ < 20_000) {
            engine.update(FRAME);
            if (engine.halftime()) engine.resumeHalf();
        }
        if (engine.goalSerial() == 0) {
            System.out.println("Celebração: a semente não produziu um golo; medição omitida.");
            canvas.dispose(); image.flush(); return;
        }
        long[] nanos = new long[SAMPLES];
        for (int frame = 0; frame < SAMPLES; frame++) {
            long before = System.nanoTime();
            engine.update(FRAME); canvas.updateVisuals(FRAME); paint(canvas, image);
            nanos[frame] = System.nanoTime() - before;
        }
        report(width + "×" + height + " / golo, overlay e confetes", nanos);
        canvas.dispose(); image.flush();
    }

    private static void paint(MatchCanvas canvas, BufferedImage image) {
        Graphics2D g = image.createGraphics();
        try { canvas.paint(g); } finally { g.dispose(); }
    }

    private static void report(String label, long[] nanos) {
        Arrays.sort(nanos);
        double median = nanos[nanos.length / 2] / 1_000_000.0;
        double p95 = nanos[(int)Math.ceil(nanos.length * .95) - 1] / 1_000_000.0;
        double mean = Arrays.stream(nanos).average().orElseThrow() / 1_000_000.0;
        System.out.printf(Locale.ROOT, "%s: mediana %.2f ms; p95 %.2f ms; média %.2f ms%n", label, median, p95, mean);
    }
}
