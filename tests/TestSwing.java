package microfm.ui;

import microfm.match.MatchCanvas;
import microfm.match.MatchEngine;
import microfm.model.Career;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.CRC32;

/** Percorre os controlos reais no EDT e testa pintura sem abrir janelas nem guardar carreiras. */
public final class TestSwing {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path output = Files.createTempDirectory("microfm-swing-test-");
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Theme.install();
                    managementFlow();
                    primeiraLigaFlow();
                    demoFlow();
                    responsiveCanvas();
                    previews(output);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            System.out.println("OK: " + checks + " verificações Swing no Event Dispatch Thread.");
        } finally {
            try (var files = Files.list(output)) {
                for (Path file : files.toList()) Files.deleteIfExists(file);
            }
            Files.deleteIfExists(output);
        }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void managementFlow() {
        check(SwingUtilities.isEventDispatchThread(), "Teste fora do EDT");
        GameWindow app = new GameWindow();
        app.setSize(1440, 920);
        check(labels(app).contains("Nova carreira"), "Ecrã inicial em falta");
        first(app, JTextField.class).setText("Inês de teste");
        click(app, text -> text.startsWith("Começar carreira"));
        check(labels(app).contains("Inês de teste"), "Nome do treinador não chegou à carreira");

        navigate(app, "Plantel");
        JTable squad = first(app, JTable.class);
        int squadSize = squad.getRowCount();
        check(squadSize >= 20 && squad.getColumnCount() == 8, "Tabela do plantel incompleta");
        click(app, text -> text.equals("Escolher melhor onze"));

        navigate(app, "Tática");
        choose(app, "4-4-2");
        choose(app, "Ofensiva");
        click(app, text -> text.startsWith("Aplicar plano de jogo"));
        check(labels(app).contains("4-4-2"), "Formação não atualizada na vista");

        navigate(app, "Treino");
        List<JButton> sessions = buttons(app).stream().filter(b -> b.getText().startsWith("Escolher sessão")).toList();
        check(sessions.size() == 3 && sessions.stream().allMatch(Component::isEnabled), "Sessões iniciais indisponíveis");
        sessions.get(1).doClick(0);
        List<JButton> completed = buttons(app).stream().filter(b -> b.getText().equals("Sessão concluída")).toList();
        check(completed.size() == 3 && completed.stream().noneMatch(Component::isEnabled), "Treino permite segunda sessão");

        navigate(app, "Mercado");
        JTable market = first(app, JTable.class);
        check(market.getRowCount() == 8, "Ofertas de mercado em falta");
        market.setRowSelectionInterval(0, 0);
        click(app, text -> text.startsWith("Contratar jogador selecionado"));
        check(first(app, JTable.class).getValueAt(0, 6).equals("Contratado"), "Compra não atualizou a oferta");
        navigate(app, "Plantel");
        check(first(app, JTable.class).getRowCount() == squadSize + 1, "Compra não atualizou o plantel");

        navigate(app, "Liga");
        check(first(app, JTable.class).getRowCount() == 8, "Classificação sem oito clubes");
        navigate(app, "Calendário");
        check(first(app, JTable.class).getRowCount() == 56, "Calendário incompleto");
        navigate(app, "Início");
        check(labels(app).contains("Centro do treinador"), "Regresso ao centro do treinador falhou");
        navigate(app, "Jogo ao vivo");
        MatchPanel match = first(app, MatchPanel.class);
        MatchEngine engine = match.engine();
        check(engine.minute() == 0 && match.participants().size() == 11, "Encontro iniciou num estado inválido");
        match.tick(.04);
        double normal = engine.minute();
        check(normal > 0, "Temporização não fez avançar o encontro");
        click(app, text -> text.contains("Pausar"));
        match.tick(.2);
        check(engine.minute() == normal, "Pausa não suspendeu o relógio");
        click(app, text -> text.contains("Retomar"));
        choose(app, "8×  Expresso");
        double before = engine.minute();
        match.tick(.04);
        check(engine.minute() - before > normal * 6, "Velocidade expresso não acelerou a simulação");
        click(app, text -> text.equals("Nomes: sim"));
        check(buttons(app).stream().anyMatch(b -> b.getText().equals("Nomes: não")), "Alternar nomes não atualizou o controlo");
        choose(app, "3-5-2");
        choose(app, "Defensiva");
        check(engine.events().stream().anyMatch(e -> e.kind().equals("tactic")), "Tática do banco não chegou ao motor");

        for (int i = 0; !engine.halftime() && !engine.finished() && i < 100; i++) engine.update(5);
        match.tick(0);
        check(engine.halftime(), "Encontro não chegou ao intervalo");
        check(button(app, text -> text.startsWith("Jogar a 2.ª parte")).isEnabled(), "Controlo do intervalo indisponível");
        click(app, text -> text.startsWith("Jogar a 2.ª parte"));
        check(!engine.halftime(), "Botão não retomou a segunda parte");
        for (int i = 0; !engine.finished() && i < 100; i++) engine.update(5);
        match.tick(0);
        check(engine.finished(), "Encontro não chegou ao final");
        check(button(app, text -> text.startsWith("Concluir jornada")).isEnabled(), "Resultado final não pode ser confirmado");
        // Não confirmar a jornada aqui: a interface guarda automaticamente no caminho de produção.
        match.dispose();
        System.out.println("  PASSOU · controlos reais: carreira, páginas, treino, compra e encontro completo");
    }

    private static void demoFlow() {
        GameWindow app = new GameWindow();
        app.setSize(1200, 800);
        click(app, text -> text.startsWith("Ver demonstração"));
        MatchPanel match = first(app, MatchPanel.class);
        check(button(app, text -> text.startsWith("Substituição")).isEnabled() == false, "Demonstração permite substituir jogadores");
        MatchEngine engine = match.engine();
        for (int i = 0; !engine.halftime() && !engine.finished() && i < 100; i++) engine.update(5);
        check(engine.halftime(), "Demo não chegou ao intervalo");
        match.tick(3.2);
        check(!engine.halftime(), "Intervalo da demonstração não retomou automaticamente");
        for (int i = 0; !engine.finished() && i < 100; i++) engine.update(5);
        match.tick(0);
        check(engine.finished(), "Demo não terminou");
        match.dispose();
        click(app, text -> text.startsWith("Voltar ao início"));
        check(labels(app).contains("Nova carreira"), "Demo não regressou ao ecrã inicial");
        System.out.println("  PASSOU · demonstração automática e regresso ao início sem gravação");
    }

    private static void primeiraLigaFlow() {
        GameWindow app = new GameWindow();
        app.setSize(1100, 740);
        choose(app, "Primeira Liga 2026/27");
        check(labels(app).contains("18 clubes") && labels(app).contains("34 jornadas"), "Resumo da Primeira Liga em falta");
        descendants(app, JToggleButton.class).stream().filter(b -> b.getText().equals("SL Benfica"))
            .findFirst().orElseThrow().doClick(0);
        click(app, text -> text.startsWith("Começar carreira"));
        check(labels(app).contains("DE 34"), "Número de jornadas incorreto no painel");
        navigate(app, "Plantel");
        JTable squad = first(app, JTable.class);
        boolean found = false;
        for (int row = 0; row < squad.getRowCount(); row++) {
            if (squad.getValueAt(row, 1).equals("Vangelis Pavlidis")) found = true;
        }
        check(found, "O Benfica não apresentou o plantel importado");
        navigate(app, "Liga");
        check(first(app, JTable.class).getRowCount() == 18, "Classificação sem 18 clubes");
        navigate(app, "Calendário");
        check(first(app, JTable.class).getRowCount() == 306, "Calendário sem os 306 jogos");
        System.out.println("  PASSOU · seleção de clube e ecrãs da Primeira Liga 2026/27");
    }

    private static void responsiveCanvas() {
        MatchEngine engine = new MatchEngine(Career.create("Pintura", 0, 9), 10);
        MatchCanvas canvas = new MatchCanvas(engine);
        for (int i = 0; i < 120; i++) { engine.update(1.0 / 60); canvas.updateVisuals(1.0 / 60); }
        double minute = engine.minute(), ballX = engine.ball().x(), ballY = engine.ball().y();
        for (int[] size : List.of(new int[]{380, 240}, new int[]{940, 540}, new int[]{1600, 900})) {
            canvas.setSize(size[0], size[1]);
            BufferedImage frame = paint(canvas);
            check(sampleColors(frame) > 50, "Campo sem detalhe no tamanho " + size[0]);
            check(engine.minute() == minute && engine.ball().x() == ballX && engine.ball().y() == ballY,
                "Pintar alterou o estado da simulação");
        }
        canvas.setSize(940, 540);
        long first = fingerprint(paint(canvas));
        for (int i = 0; i < 90; i++) { engine.update(1.0 / 60); canvas.updateVisuals(1.0 / 60); }
        check(first != fingerprint(paint(canvas)), "A imagem do encontro não mudou com o tempo");
        canvas.dispose();
        System.out.println("  PASSOU · campo responsivo, animação e pintura sem efeitos no motor");
    }

    private static void previews(Path output) throws Exception {
        GameWindow.renderPreviews(output);
        List<Path> images;
        try (var files = Files.list(output)) { images = files.filter(p -> p.toString().endsWith(".png")).toList(); }
        check(images.size() == 9, "Devem existir nove pré-visualizações");
        Set<Long> different = new HashSet<>();
        for (Path path : images) {
            BufferedImage image = ImageIO.read(path.toFile());
            check(image != null && image.getWidth() == 1440 && image.getHeight() == 920, "Dimensões de pré-visualização inválidas: " + path);
            check(!image.getColorModel().hasAlpha(), "Pré-visualização inesperadamente transparente");
            check(sampleColors(image) > 15 && Files.size(path) > 15_000, "Pré-visualização vazia: " + path);
            different.add(fingerprint(image));
        }
        check(different.size() == 9, "Duas páginas produziram exatamente a mesma imagem");
        System.out.println("  PASSOU · nove páginas distintas renderizadas em PNG opaco de 1440 × 920");
    }

    private static BufferedImage paint(JComponent component) {
        layout(component);
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        component.printAll(graphics);
        graphics.dispose();
        return image;
    }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) layout(child);
        }
    }

    private static int sampleColors(BufferedImage image) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 9)
            for (int x = 0; x < image.getWidth(); x += 9) colors.add(image.getRGB(x, y));
        return colors.size();
    }

    private static long fingerprint(BufferedImage image) {
        CRC32 crc = new CRC32();
        for (int y = 0; y < image.getHeight(); y += 3)
            for (int x = 0; x < image.getWidth(); x += 3) {
                int rgb = image.getRGB(x, y);
                crc.update(rgb); crc.update(rgb >> 8); crc.update(rgb >> 16);
            }
        return crc.getValue();
    }

    private static void navigate(GameWindow app, String page) {
        click(app, text -> text.strip().endsWith(page));
    }

    private static void choose(Container app, String choice) {
        for (JComboBox<?> combo : descendants(app, JComboBox.class))
            for (int i = 0; i < combo.getItemCount(); i++)
                if (choice.equals(combo.getItemAt(i))) { combo.setSelectedIndex(i); return; }
        throw new AssertionError("Escolha não encontrada: " + choice);
    }

    private static String labels(Container app) {
        return descendants(app, JLabel.class).stream().map(JLabel::getText).filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static void click(Container app, Predicate<String> text) { button(app, text).doClick(0); }

    private static JButton button(Container app, Predicate<String> text) {
        return buttons(app).stream().filter(b -> b.getText() != null && text.test(b.getText())).findFirst()
            .orElseThrow(() -> new AssertionError("Botão não encontrado entre " + buttons(app).stream().map(AbstractButton::getText).toList()));
    }

    private static List<JButton> buttons(Container app) { return descendants(app, JButton.class); }

    private static <T> T first(Container app, Class<T> type) {
        return descendants(app, type).stream().findFirst().orElseThrow(() -> new AssertionError("Componente não encontrado: " + type));
    }

    private static <T> List<T> descendants(Container app, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : app.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container nested) result.addAll(descendants(nested, type));
        }
        return result;
    }
}
