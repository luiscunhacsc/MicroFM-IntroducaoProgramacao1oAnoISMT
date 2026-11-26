package microfm.model;

public final class MarketOffer {
    private final Player player;
    private final int price;
    private boolean signed;
    MarketOffer(Player player, int price, boolean signed) {
        if (player == null || price < 0) throw new IllegalArgumentException("Oferta inválida.");
        this.player = player; this.price = price; this.signed = signed;
    }
    public Player player() { return player; }
    public int price() { return price; }
    public boolean signed() { return signed; }
    void markSigned() { signed = true; }
}
