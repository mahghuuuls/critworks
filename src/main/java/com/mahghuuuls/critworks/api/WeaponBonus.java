package com.mahghuuuls.critworks.api;

/** One weapon-specific bonus a provider gives a stack: which stat, how much, and the source shown to players. */
public final class WeaponBonus {

    private final CritStat stat;
    private final double amount;
    private final String source;

    /**
     * @param amount in the stat's own unit (percent for increased, points for additional chance, multiplier
     *               points for additional damage)
     * @param source a short label such as "mymod:sharpened", shown by inspection and tooltips
     */
    public WeaponBonus(CritStat stat, double amount, String source) {
        if (stat == null) {
            throw new IllegalArgumentException("stat must not be null");
        }
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("source must not be empty");
        }
        this.stat = stat;
        this.amount = amount;
        this.source = source;
    }

    public CritStat getStat() {
        return stat;
    }

    public double getAmount() {
        return amount;
    }

    public String getSource() {
        return source;
    }
}
