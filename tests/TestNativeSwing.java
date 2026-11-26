package microfm.ui;

import microfm.model.Career;
import microfm.model.CareerStore;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Teste opcional de janela nativa; executar só numa pasta nova em build/native-smoke. */
public final class TestNativeSwing {
    private static int checks;
    private static final Path SAVE = Path.of("carreira-swing.microfm");
    private static final AtomicReference<Throwable> asyncFailure = new AtomicReference<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("--native"))
            throw new IllegalArgumentException("Teste opcional: usar --native numa pasta nova dentro de build/native-smoke.");
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Este teste precisa de um ambiente gráfico.");
        String cwd = Path.of("").toAbsolutePath().normalize().toString().replace('\\', '/');
        if (!cwd.contains("/build/native-smoke")) throw new IllegalStateException("Pasta de execução deve estar dentro de build/native-smoke.");
        if (Files.exists(SAVE)) throw new IllegalStateException("A pasta já contém uma carreira; não será alterada.");
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> asyncFailure.compareAndSet(null, error));
        try {
            GameWindow.launch(new String[0]);
            await(() -> frame() != null, "A janela inicial não abriu");
            JFrame first = edt(TestNativeSwing::frame);
            edt(() -> {
                GameWindow app = (GameWindow) first.getContentPane();
                find(app, JTextField.class).setText("Inês · Teste nativo");
                click(app, t -> t.startsWith("Começar carreira"));
                click(app, t -> t.strip().endsWith("Jogo ao vivo"));
                return null;
            });
            MatchPanel match = edt(() -> find((Container) first.getContentPane(), MatchPanel.class));
            double start = edt(() -> match.engine().minute());
            await(() -> match.engine().minute() > start + .05, "Timer da janela não fez avançar o encontro");
            check(edt(first::isShowing), "Janela não está visível");
            screenshot(first, "live.png");
            int[] result = edt(() -> {
                for (int i = 0; !match.engine().finished() && i < 100; i++) {
                    match.engine().update(5);
                    if (match.engine().halftime()) match.engine().resumeHalf();
                }
                match.tick(0);
                check(match.engine().finished(), "Encontro não terminou");
                int[] goals = {match.engine().homeGoals(), match.engine().awayGoals()};
                // O fecho precisa de aplicar o resultado, mesmo sem clicar em Concluir jornada.
                first.dispatchEvent(new WindowEvent(first, WindowEvent.WINDOW_CLOSING));
                return goals;
            });
            await(() -> !first.isDisplayable(), "Primeira janela não fechou");
            Career saved = CareerStore.load(SAVE);
            check(saved.round() == 1, "Fechar após o apito final perdeu a jornada");
            check(saved.fixtures().get(0).homeGoals() == result[0] && saved.fixtures().get(0).awayGoals() == result[1],
                "Resultado gravado difere do encontro");
            check(saved.manager().equals("Inês · Teste nativo"), "Nome português não foi conservado");
            check(saved.squad().stream().mapToInt(p -> p.goals()).sum() == result[0], "Marcadores não foram gravados");
            System.out.println("  PASSOU · Timer nativo e fechar após o apito final guarda a jornada");

            GameWindow.launch(new String[0]);
            await(() -> frame() != null, "Segunda janela não abriu");
            JFrame second = edt(TestNativeSwing::frame);
            edt(() -> { click((Container) second.getContentPane(), t -> t.equals("Continuar carreira")); return null; });
            await(() -> texts((Container) second.getContentPane()).contains("Bem-vindo de volta"), "Carregamento assíncrono não terminou");
            edt(() -> {
                Container app = (Container) second.getContentPane();
                click(app, t -> t.strip().endsWith("Liga"));
                JTable table = find(app, JTable.class);
                check(table.getRowCount() == 8, "Tabela carregada incompleta");
                for (int row = 0; row < 8; row++) check(table.getValueAt(row, 2).equals(1), "Classificação perdeu a jornada");
                click(app, t -> t.strip().endsWith("Treino"));
                button(app, t -> t.startsWith("Escolher sessão")).doClick(0);
                check(buttons(app).stream().filter(b -> b.getText().equals("Sessão concluída")).count() == 3, "Treino não foi aplicado");
                click(app, t -> t.equals("Guardar carreira"));
                return null;
            });
            await(() -> texts((Container) second.getContentPane()).contains("Carreira guardada"), "Gravação assíncrona não terminou");
            edt(() -> {
                Container app = (Container) second.getContentPane();
                List<JButton> sessions = buttons(app).stream().filter(b -> b.getText().equals("Sessão concluída")).toList();
                check(sessions.size() == 3 && sessions.stream().noneMatch(Component::isEnabled),
                    "Guardar voltou a ativar sessões de treino já gastas");
                return null;
            });
            check(CareerStore.load(SAVE).trained(), "Gravação assíncrona perdeu o treino");
            screenshot(second, "loaded.png");
            edt(() -> { second.dispatchEvent(new WindowEvent(second, WindowEvent.WINDOW_CLOSING)); return null; });
            await(() -> frame() == null, "Segunda janela não fechou");
            check(asyncFailure.get() == null, "Exceção assíncrona: " + asyncFailure.get());
            System.out.println("  PASSOU · carregar, treinar e guardar com SwingWorker e regras dos controlos preservadas");
            System.out.println("OK: " + checks + " verificações nativas. Artefactos em " + cwd);
        } finally {
            edt(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof JFrame f && f.getContentPane() instanceof GameWindow app) {
                        List<MatchPanel> matches = descendants(app, MatchPanel.class);
                        matches.forEach(MatchPanel::dispose);
                    }
                    window.dispose();
                }
                return null;
            });
        }
    }

    private static void check(boolean success, String message) { checks++; if (!success) throw new AssertionError(message); }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + 12_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (asyncFailure.get() != null) throw new AssertionError("Falha assíncrona", asyncFailure.get());
            if (edt(condition::getAsBoolean)) { checks++; return; }
            Thread.sleep(40);
        }
        throw new AssertionError(message);
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return action.call();
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { value.set(action.call()); } catch (Throwable e) { error.set(e); } });
        if (error.get() != null) throw new AssertionError("Falha no EDT", error.get());
        return value.get();
    }

    private static JFrame frame() {
        for (Frame f : Frame.getFrames()) if (f instanceof JFrame j && j.isShowing() && j.getContentPane() instanceof GameWindow) return j;
        return null;
    }

    private static void screenshot(JFrame frame, String name) throws Exception {
        Rectangle bounds = edt(() -> new Rectangle(frame.getLocationOnScreen(), frame.getSize()));
        try { ImageIO.write(new Robot().createScreenCapture(bounds), "png", Path.of(name).toFile()); }
        catch (AWTException | SecurityException ex) { System.out.println("  Captura opcional indisponível: " + ex.getMessage()); }
    }

    private static String texts(Container app) {
        return descendants(app, JLabel.class).stream().map(JLabel::getText).filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.joining("\n"));
    }
    private static void click(Container app, Predicate<String> text) { button(app, text).doClick(0); }
    private static JButton button(Container app, Predicate<String> text) {
        return buttons(app).stream().filter(b -> b.getText() != null && text.test(b.getText())).findFirst()
            .orElseThrow(() -> new AssertionError("Botão em falta"));
    }
    private static List<JButton> buttons(Container app) { return descendants(app, JButton.class); }
    private static <T> T find(Container app, Class<T> type) {
        return descendants(app, type).stream().findFirst().orElseThrow(() -> new AssertionError("Componente em falta: " + type));
    }
    private static <T> List<T> descendants(Container app, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component c : app.getComponents()) {
            if (type.isInstance(c)) result.add(type.cast(c));
            if (c instanceof Container nested) result.addAll(descendants(nested, type));
        }
        return result;
    }
}
