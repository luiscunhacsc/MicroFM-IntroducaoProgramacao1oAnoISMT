package microfm.model;

/** Uma posição é um valor do domínio, não um número mágico. */
public enum Position {
    GOALKEEPER("GR"), DEFENDER("DEF"), MIDFIELDER("MED"), FORWARD("AVA");
    private final String label;
    Position(String label) { this.label = label; }
    public String label() { return label; }
}
