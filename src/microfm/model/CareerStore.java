package microfm.model;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Repositório de ficheiros de texto: sem serialização de objetos nem dependências. */
public final class CareerStore {
    public static final Path DEFAULT_PATH = Path.of("carreira-swing.microfm");
    private static final int FORMAT_VERSION = 2;
    private CareerStore() { }

    public static void save(Career career, Path path) throws IOException {
        if (career == null || path == null) throw new IllegalArgumentException("Carreira ou caminho em falta.");
        Properties data = new Properties();
        put(data, "format", "MICROFM-SWING"); put(data, "version", FORMAT_VERSION);
        put(data, "league", career.league().name());
        put(data, "manager", career.manager()); put(data, "club", career.club().id());
        put(data, "seed", career.seed()); put(data, "season", career.season());
        put(data, "round", career.round()); put(data, "balance", career.balance());
        put(data, "morale", career.morale()); put(data, "formation", career.formation().name());
        put(data, "mentality", career.mentality().name()); put(data, "trained", career.trained());
        List<Player> squad = career.squad();
        List<Player> lineup = career.lineup();
        List<MarketOffer> market = career.market();
        List<Fixture> fixtures = career.fixtures();
        put(data, "squad.count", squad.size());
        for (int i = 0; i < squad.size(); i++) writePlayer(data, "squad." + i + ".", squad.get(i));
        for (int i = 0; i < 11; i++) put(data, "lineup." + i, lineup.get(i).id());
        for (int i = 0; i < market.size(); i++) {
            MarketOffer offer = market.get(i); String prefix = "market." + i + ".";
            put(data, prefix + "signed", offer.signed()); put(data, prefix + "price", offer.price());
            if (offer.signed()) put(data, prefix + "id", offer.player().id());
            else writePlayer(data, prefix, offer.player());
        }
        for (int i = 0; i < fixtures.size(); i++) {
            Fixture fixture = fixtures.get(i); String prefix = "fixture." + i + ".";
            put(data, prefix + "round", fixture.round()); put(data, prefix + "home", fixture.homeId());
            put(data, prefix + "away", fixture.awayId()); put(data, prefix + "hg", fixture.homeGoals());
            put(data, prefix + "ag", fixture.awayGoals());
        }
        List<String> news = career.news();
        put(data, "news.count", news.size());
        for (int i = 0; i < news.size(); i++) put(data, "news." + i, news.get(i));

        // Primeiro escreve-se tudo ao lado do destino; só depois se substitui o ficheiro antigo.
        Path destination = path.toAbsolutePath().normalize();
        Path directory = destination.getParent(); Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".microfm-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                data.store(writer, "MicroFM Swing - carreira v" + FORMAT_VERSION + " - UTF-8");
            }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static Career load(Path path) throws IOException {
        if (path == null) throw new IllegalArgumentException("Caminho em falta.");
        if (Files.size(path) > 262_144) throw new IOException("O ficheiro de carreira excede o tamanho permitido.");
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { data.load(reader); }
        catch (IllegalArgumentException e) { throw new IOException("O ficheiro de carreira contém texto inválido.", e); }
        try {
            if (!"MICROFM-SWING".equals(required(data, "format"))) throw new IllegalArgumentException("Formato desconhecido.");
            int version = integer(data, "version", 1, FORMAT_VERSION);
            League league = version == 1 ? League.FICTIONAL : League.valueOf(required(data, "league"));
            int lastClubId = league.clubs().size() - 1;
            int rounds = league.roundCount();
            int fixtureCount = league.fixtureCount();
            int squadCount = integer(data, "squad.count", 20, league.squadLimit());
            List<Player> squad = new ArrayList<>(); Map<Integer, Player> byId = new HashMap<>();
            for (int i = 0; i < squadCount; i++) {
                Player player = readPlayer(data, "squad." + i + ".");
                if (byId.put(player.id(), player) != null) throw new IllegalArgumentException("Jogador repetido.");
                squad.add(player);
            }
            List<Integer> lineup = new ArrayList<>();
            for (int i = 0; i < 11; i++) lineup.add(integer(data, "lineup." + i, 0, 999_999));
            List<MarketOffer> market = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String prefix = "market." + i + "."; boolean signed = bool(data, prefix + "signed");
                Player player = signed ? byId.get(integer(data, prefix + "id", 0, 999_999)) : readPlayer(data, prefix);
                if (player == null) throw new IllegalArgumentException("Contratação fora do plantel.");
                market.add(new MarketOffer(player, integer(data, prefix + "price", 0, 10_000_000), signed));
            }
            List<Fixture> fixtures = new ArrayList<>(fixtureCount);
            for (int i = 0; i < fixtureCount; i++) {
                String prefix = "fixture." + i + ".";
                Fixture fixture = new Fixture(integer(data, prefix + "home", 0, lastClubId),
                    integer(data, prefix + "away", 0, lastClubId), integer(data, prefix + "round", 0, rounds - 1));
                int hg = integer(data, prefix + "hg", -1, 99), ag = integer(data, prefix + "ag", -1, 99);
                if ((hg < 0) != (ag < 0)) throw new IllegalArgumentException("Resultado incompleto.");
                if (hg >= 0) fixture.result(hg, ag);
                fixtures.add(fixture);
            }
            List<String> news = new ArrayList<>(); int newsCount = integer(data, "news.count", 0, 24);
            for (int i = 0; i < newsCount; i++) news.add(required(data, "news." + i));
            return Career.restore(required(data, "manager"), integer(data, "club", 0, lastClubId),
                Long.parseLong(required(data, "seed")), integer(data, "season", 1, 30),
                integer(data, "round", 0, rounds), integer(data, "balance", 0, 1_000_000_000),
                integer(data, "morale", 25, 100), Formation.valueOf(required(data, "formation")),
                Mentality.valueOf(required(data, "mentality")), bool(data, "trained"), squad, lineup, market, fixtures, news, league);
        } catch (IllegalArgumentException e) {
            throw new IOException("Não foi possível abrir a carreira: o ficheiro é inválido ou incompatível.", e);
        }
    }

    private static void put(Properties data, String key, Object value) { data.setProperty(key, String.valueOf(value)); }
    private static String required(Properties data, String key) {
        String value = data.getProperty(key);
        if (value == null || value.length() > 400) throw new IllegalArgumentException("Campo em falta ou demasiado longo: " + key);
        return value;
    }
    private static int integer(Properties data, String key, int min, int max) {
        int value = Integer.parseInt(required(data, key));
        if (value < min || value > max) throw new IllegalArgumentException("Campo fora dos limites: " + key);
        return value;
    }
    private static boolean bool(Properties data, String key) {
        String value = required(data, key);
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Valor lógico inválido.");
        return Boolean.parseBoolean(value);
    }
    private static void writePlayer(Properties data, String prefix, Player player) {
        put(data, prefix + "id", player.id()); put(data, prefix + "name", player.name());
        put(data, prefix + "position", player.position().name()); put(data, prefix + "age", player.age());
        put(data, prefix + "quality", player.quality()); put(data, prefix + "condition", player.condition());
        put(data, prefix + "goals", player.goals()); put(data, prefix + "appearances", player.appearances());
        put(data, prefix + "injured", player.injuredRounds());
    }
    private static Player readPlayer(Properties data, String prefix) {
        return new Player(integer(data, prefix + "id", 0, 999_999), required(data, prefix + "name"),
            Position.valueOf(required(data, prefix + "position")), integer(data, prefix + "age", Player.MIN_AGE, Player.MAX_AGE),
            integer(data, prefix + "quality", 1, 99), integer(data, prefix + "condition", 0, 100),
            integer(data, prefix + "goals", 0, 1_000_000), integer(data, prefix + "appearances", 0, 100_000),
            integer(data, prefix + "injured", 0, 52));
    }
}
