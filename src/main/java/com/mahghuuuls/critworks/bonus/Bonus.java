package com.mahghuuuls.critworks.bonus;

import com.mahghuuuls.critworks.api.CritStat;

/** One bonus that counts for an attack: which stat, how much, and where it comes from. */
public final class Bonus {

    private final CritStat stat;
    private final double amount;
    private final String source;

    public Bonus(CritStat stat, double amount, String source) {
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
