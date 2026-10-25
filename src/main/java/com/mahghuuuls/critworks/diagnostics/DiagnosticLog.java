package com.mahghuuuls.critworks.diagnostics;

import org.apache.logging.log4j.Logger;

import java.util.Locale;

/**
 * Writes the diagnostic mode lines: one per resolved supported attack, never per tick. Off by default;
 * when off, nothing is formatted or written.
 */
public final class DiagnosticLog {

    private final Logger logger;
    private final boolean enabled;

    public DiagnosticLog(Logger logger, boolean enabled) {
        this.logger = logger;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Writes one prepared line when diagnostic mode is on. */
    public void write(Line line) {
        if (enabled) {
            logger.info(line.toString());
        }
    }

    /** The one line of a resolved attack: "[diag] attack melee ..." or "[diag] attack arrow ...". */
    public Line line(String kind) {
        return new Line("[diag] attack ", kind);
    }

    /** The short line of one successful hit by a critical arrow: "[diag] hit arrow ...". */
    public Line hitLine(String kind) {
        return new Line("[diag] hit ", kind);
    }

    /** Builds one "key=value" line; values with spaces stay readable because keys never contain spaces. */
    public static final class Line {
        private final StringBuilder text;

        private Line(String prefix, String kind) {
            text = new StringBuilder(prefix).append(kind);
        }

        public Line add(String key, Object value) {
            text.append(' ').append(key).append('=').append(value);
            return this;
        }

        public Line add(String key, double value) {
            text.append(' ').append(key).append('=').append(format(value));
            return this;
        }

        public static String format(double value) {
            if (Double.isNaN(value)) {
                return "-";
            }
            if (Double.isInfinite(value)) {
                return "none";
            }
            return String.format(Locale.ROOT, "%.2f", value);
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
