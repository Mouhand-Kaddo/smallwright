package dev.smallwright.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The optional limits a person sets in Settings for each run. Every limit is off unless set. A step
 * is one model request with the actions it asks for; spending is what OpenRouter reported for them.
 * A model server reports no cost, so a spending limit refuses a run with it.
 */
final class RunLimits {
    static final RunLimits OFF = new RunLimits(0, null, false);

    static final int FIXES_BEFORE_STUCK = 3;
    private static final int MAX_STEPS = 10_000;
    private static final BigDecimal MAX_COST_USD = new BigDecimal("10000");

    /** Steps allowed in a run; 0 means no limit. */
    final int maxSteps;

    /** Spending allowed in a run, in US dollars; null means no limit. */
    final BigDecimal maxCostUsd;

    final boolean stopWhenStuck;

    RunLimits(int maxSteps, BigDecimal maxCostUsd, boolean stopWhenStuck) {
        this.maxSteps = maxSteps;
        this.maxCostUsd = maxCostUsd;
        this.stopWhenStuck = stopWhenStuck;
    }

    /** A whole number of steps, or empty for no limit. */
    static int parseSteps(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        try {
            int steps = Integer.parseInt(trimmed);
            if (steps >= 1 && steps <= MAX_STEPS) return steps;
        } catch (NumberFormatException notANumber) {
        }
        throw new IllegalArgumentException(
                "Steps: use a whole number from 1 to " + MAX_STEPS + ", or leave it empty.");
    }

    /** Dollars such as 0.50 or $2, or empty for no limit. */
    static BigDecimal parseCostUsd(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("$")) trimmed = trimmed.substring(1).trim();
        if (trimmed.isEmpty()) return null;
        try {
            BigDecimal cost = new BigDecimal(trimmed);
            if (cost.signum() > 0 && cost.compareTo(MAX_COST_USD) <= 0) return cost;
        } catch (NumberFormatException notANumber) {
        }
        throw new IllegalArgumentException(
                "Spending: use dollars above 0, such as 0.50 or 2, or leave it empty.");
    }

    /** Why Send is refused for {@code model} with these limits, or null when it is not. */
    String refusal(dev.smallwright.agent.ModelChoice model) {
        if (maxCostUsd == null || model.provider.isOpenRouter()) return null;
        return "Your spending limit can't be kept with a model server, which doesn't report what"
                + " requests cost. Clear the limit in Settings or pick an OpenRouter model."
                + " Nothing was sent.";
    }

    static String money(BigDecimal dollars) {
        BigDecimal shown = dollars.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        return "$" + (shown.scale() < 2 ? shown.setScale(2) : shown).toPlainString();
    }

    Watch watch() {
        return new Watch();
    }

    /** What one run has done so far, against these limits. */
    final class Watch {
        private String lastError;
        private int sameErrors;

        void buildFailed(String errorSignature) {
            sameErrors = errorSignature.equals(lastError) ? sameErrors + 1 : 1;
            lastError = errorSignature;
        }

        void buildSucceeded() {
            lastError = null;
            sameErrors = 0;
        }

        /** The same build error has come back after the allowed number of fixes. */
        boolean stuck() {
            return stopWhenStuck && sameErrors > FIXES_BEFORE_STUCK;
        }

        /** The builder's message naming the limit the run has reached, or null when none is. */
        String reached(int steps, BigDecimal spent) {
            String stopped;
            if (stuck())
                stopped =
                        "I stopped because the same build error came back after "
                                + FIXES_BEFORE_STUCK
                                + " fixes in a row. You turned on Stop when stuck in Settings.";
            else if (maxSteps > 0 && steps >= maxSteps)
                stopped =
                        "I stopped after "
                                + steps
                                + (steps == 1 ? " step" : " steps")
                                + ", the step limit you set in Settings.";
            else if (maxCostUsd != null && spent.compareTo(maxCostUsd) >= 0)
                stopped =
                        "I stopped after spending "
                                + money(spent)
                                + ", which reached the spending limit of "
                                + money(maxCostUsd)
                                + " you set in Settings.";
            else return null;
            return stopped + " The changes so far are saved. Send a message to go on.";
        }
    }
}
