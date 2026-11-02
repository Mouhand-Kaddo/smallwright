package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.controller.ui.UiState;
import java.util.concurrent.TimeUnit;

/**
 * How probes reach the Compose screen. Reads go through the controller's state holders by field
 * name; taps go through accessibility, which is also what a finger does.
 */
final class Ui {
    private Ui() {}

    /** A field of the screen, or of the run coordinator the screen observes. */
    static Object field(Object activity, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field value = declared(name);
        return value.get(holder(activity, value));
    }

    static void setField(Object activity, String name, Object next)
            throws ReflectiveOperationException {
        java.lang.reflect.Field value = declared(name);
        value.set(holder(activity, value), next);
    }

    private static java.lang.reflect.Field declared(String name) throws NoSuchFieldException {
        java.lang.reflect.Field value;
        try {
            value = MainActivity.class.getDeclaredField(name);
        } catch (NoSuchFieldException moved) {
            value = RunCoordinator.class.getDeclaredField(name);
        }
        value.setAccessible(true);
        return value;
    }

    private static Object holder(Object activity, java.lang.reflect.Field value)
            throws ReflectiveOperationException {
        if (value.getDeclaringClass() != RunCoordinator.class) return activity;
        java.lang.reflect.Field runs = MainActivity.class.getDeclaredField("runs");
        runs.setAccessible(true);
        return runs.get(activity);
    }

    static TextBox text(Activity activity, String name) throws Exception {
        return (TextBox) field(activity, name);
    }

    static Control control(Activity activity, String name) throws Exception {
        return (Control) field(activity, name);
    }

    static Picker picker(Activity activity, String name) throws Exception {
        return (Picker) field(activity, name);
    }

    static UiState state(Activity activity) throws Exception {
        return (UiState) field(activity, "ui");
    }

    /** The control whose label is shown, or an AssertionError naming the missing action. */
    static Control button(Activity activity, String label) throws Exception {
        for (java.lang.reflect.Field value : MainActivity.class.getDeclaredFields()) {
            if (value.getType() != Control.class) continue;
            value.setAccessible(true);
            Control control = (Control) value.get(activity);
            if (control != null && label.equals(control.getLabel())) return control;
        }
        throw new AssertionError("Missing action: " + label);
    }

    /**
     * Taps the visible, enabled button with this label through accessibility. Call it from the
     * instrumentation thread, never from the main thread.
     */
    static void tap(Instrumentation in, Context target, String label) throws Exception {
        AccessibilityNodeInfo node = node(in, target, "android.widget.Button", label);
        if (!node.isEnabled()) throw new AssertionError("Action disabled: " + label);
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
        node = node(in, target, "android.widget.Button", label);
        if (!node.isVisibleToUser() || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            throw new AssertionError("Action not tappable: " + label);
        in.waitForIdleSync();
    }

    static boolean shown(Instrumentation in, Context target, String label) {
        AccessibilityNodeInfo root = in.getUiAutomation().getRootInActiveWindow();
        if (root == null || !target.getPackageName().equals(String.valueOf(root.getPackageName())))
            return false;
        AccessibilityNodeInfo found = find(root, "android.widget.Button", label);
        return found != null && found.isVisibleToUser();
    }

    static void openMenu(Instrumentation in, Activity ui) throws Exception {
        if (state(ui).getMenuOpen()) return;
        if (state(ui).getToolsOpen() || state(ui).getSettingsOpen()) closePages(in, ui);
        tap(in, ui, Labels.MENU);
    }

    /** Opens the project tools the way a person does: a long press on the project name. */
    static void showTools(Instrumentation in, Context target, Activity ui) throws Exception {
        if (state(ui).getToolsOpen()) return;
        if (state(ui).getMenuOpen() || state(ui).getSettingsOpen()) closePages(in, ui);
        Picker projects = state(ui).getProjectPicker();
        String name = projects.getItems().get(projects.getSelectedItemPosition());
        AccessibilityNodeInfo node = node(in, target, BUTTON, name);
        if (!node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            throw new AssertionError("Action not tappable: " + Labels.TOOLS);
        in.waitForIdleSync();
    }

    /** Runs the control's action as a tap would, without the screen. Prefer tap. */
    static boolean click(Control control) {
        if (!control.isEnabled()) return false;
        control.getOnClick().run();
        return true;
    }

    static void closePages(Instrumentation in, Activity ui) {
        in.runOnMainSync(
                () -> {
                    try {
                        UiState state = state(ui);
                        state.setToolsOpen(false);
                        state.setSettingsOpen(false);
                        state.setMenuOpen(false);
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                });
        in.waitForIdleSync();
    }

    static AccessibilityNodeInfo node(Instrumentation in, Context target, String type, String text)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            AccessibilityNodeInfo root = in.getUiAutomation().getRootInActiveWindow();
            if (root != null
                    && target.getPackageName().equals(String.valueOf(root.getPackageName()))) {
                AccessibilityNodeInfo found = find(root, type, text);
                if (found != null) return found;
            }
            Thread.sleep(100);
        }
        AccessibilityNodeInfo root = in.getUiAutomation().getRootInActiveWindow();
        StringBuilder seen = new StringBuilder();
        texts(root, seen);
        for (AccessibilityNodeInfo up = find(root, null, text); up != null; up = up.getParent())
            seen.append(" ^")
                    .append(up.getClassName())
                    .append(up.isClickable() ? "/clickable" : "")
                    .append(up.isEnabled() ? "" : "/disabled");
        String message =
                "Visible control unavailable: "
                        + text
                        + " in "
                        + (root == null ? "no window" : root.getPackageName())
                        + " showing"
                        + seen;
        android.util.Log.w("Ui", message);
        throw new AssertionError(message);
    }

    private static void texts(AccessibilityNodeInfo node, StringBuilder into) {
        if (node == null) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0 && !node.isEditable() && !node.isPassword())
            into.append(" [")
                    .append(node.getClassName())
                    .append(':')
                    .append(text, 0, Math.min(text.length(), 80))
                    .append(']');
        for (int i = 0; i < node.getChildCount(); i++) texts(node.getChild(i), into);
    }

    /**
     * Compose exposes a button's label on a child text node and the click on a plain clickable
     * ancestor, so a button match starts at the label and climbs to the nearest node of the wanted
     * type or, for buttons, the nearest clickable one. Exact text wins over a case-insensitive
     * match, which platform dialog buttons need.
     */
    static AccessibilityNodeInfo find(AccessibilityNodeInfo node, String type, String text) {
        java.util.List<AccessibilityNodeInfo> labelled = new java.util.ArrayList<>();
        byText(node, text, true, labelled);
        if (labelled.isEmpty()) byText(node, text, false, labelled);
        boolean climbs = type == null || BUTTON.equals(type);
        for (AccessibilityNodeInfo label : labelled) {
            for (AccessibilityNodeInfo candidate = label;
                    candidate != null;
                    candidate = climbs ? candidate.getParent() : null) {
                if (type != null && type.contentEquals(candidate.getClassName())) return candidate;
                if (climbs && candidate.isClickable()) return candidate;
            }
        }
        return type == null && !labelled.isEmpty() ? labelled.get(0) : null;
    }

    private static final String BUTTON = "android.widget.Button";

    private static void byText(
            AccessibilityNodeInfo node,
            String text,
            boolean exact,
            java.util.List<AccessibilityNodeInfo> into) {
        if (node == null) return;
        CharSequence shown = node.getText() != null ? node.getText() : node.getContentDescription();
        String own = String.valueOf(shown);
        if (exact ? text.equals(own) : text.equalsIgnoreCase(own)) into.add(node);
        for (int i = 0; i < node.getChildCount(); i++) byText(node.getChild(i), text, exact, into);
    }
}
