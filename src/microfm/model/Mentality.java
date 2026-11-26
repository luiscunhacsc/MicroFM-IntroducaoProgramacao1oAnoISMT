package microfm.model;

public enum Mentality {
    DEFENSIVE("Defensiva"), BALANCED("Equilibrada"), ATTACKING("Ofensiva");
    private final String label;
    Mentality(String label) { this.label = label; }
    public String label() { return label; }
}
