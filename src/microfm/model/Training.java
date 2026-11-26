package microfm.model;

public enum Training {
    RECOVERY("Recuperação"), TECHNICAL("Técnica"), INTENSIVE("Intensivo");
    private final String label;
    Training(String label) { this.label = label; }
    public String label() { return label; }
}
