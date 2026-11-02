package dev.smallwright.controller;

import android.content.Context;
import android.content.SharedPreferences;
import java.math.BigDecimal;

/** Keeps the run limits from Settings on this phone. Nothing is stored until a limit is set. */
final class RunSettings {
    private static final String FILE = "run-limits";
    private static final String STEPS = "steps";
    private static final String COST = "cost";
    private static final String STUCK = "stop-when-stuck";
    private static final String ASK_LIBRARIES = "ask-before-libraries";
    private static final String ASK_SKILLS = "ask-before-skills";

    private RunSettings() {}

    static RunLimits load(Context context) {
        SharedPreferences stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        try {
            return new RunLimits(
                    stored.getInt(STEPS, 0),
                    RunLimits.parseCostUsd(stored.getString(COST, "")),
                    stored.getBoolean(STUCK, false));
        } catch (IllegalArgumentException unreadable) {
            return RunLimits.OFF;
        }
    }

    static boolean asksBeforeLibraries(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(ASK_LIBRARIES, false);
    }

    static boolean saveAsksBeforeLibraries(Context context, boolean asks) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(ASK_LIBRARIES, asks)
                .commit();
    }

    static boolean asksBeforeSkills(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(ASK_SKILLS, false);
    }

    static boolean saveAsksBeforeSkills(Context context, boolean asks) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(ASK_SKILLS, asks)
                .commit();
    }

    static boolean save(Context context, RunLimits limits) {
        BigDecimal cost = limits.maxCostUsd;
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putInt(STEPS, limits.maxSteps)
                .putString(COST, cost == null ? "" : cost.toPlainString())
                .putBoolean(STUCK, limits.stopWhenStuck)
                .commit();
    }
}
