package microfm.model;

import java.util.List;

public record Club(int id, String name, String shortName, String city, int rgb, int strength) {
    private static final List<Club> CLUBS = List.of(
        new Club(0, "Académicos do Mondego", "ACM", "Coimbra", 0x4ED7BD, 72),
        new Club(1, "Atlético de Lisboa", "ATL", "Lisboa", 0xEC666A, 75),
        new Club(2, "União Invicta", "UNI", "Porto", 0x65A9F5, 74),
        new Club(3, "Vitória da Serra", "VDS", "Covilhã", 0xAD8CF4, 71),
        new Club(4, "Estrela do Algarve", "EDA", "Faro", 0xF4BC66, 69),
        new Club(5, "Marítimos da Costa", "MDC", "Aveiro", 0x59C8DD, 70),
        new Club(6, "Ferroviários do Centro", "FDC", "Entroncamento", 0xED8F63, 67),
        new Club(7, "Lusitanos do Alentejo", "LDA", "Évora", 0xB9D36A, 68));
    public static List<Club> all() { return CLUBS; }
}
