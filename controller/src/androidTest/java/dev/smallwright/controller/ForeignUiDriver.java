package dev.smallwright.controller;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Test assistance for functional checklists: reads, clicks and sets text in the foreground
 * generated app through accessibility actions, without injecting key events. Text entry through
 * ACTION_SET_TEXT bypasses the keyboard and must be labelled as assistance.
 */
final class ForeignUiDriver {
    private ForeignUiDriver() {}

    static String run(Instrumentation in, String action, String target, String value)
            throws Exception {
        AccessibilityNodeInfo root = generatedRoot(in);
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes);
        if ("texts".equals(action)) {
            StringBuilder out = new StringBuilder(String.valueOf(root.getPackageName()));
            for (AccessibilityNodeInfo node : nodes) {
                CharSequence text = node.getText();
                if (text == null && !node.isEditable() && !node.isCheckable()) continue;
                out.append('\n')
                        .append(node.isEditable() ? "edit" : node.isClickable() ? "click" : "text")
                        .append(" | ")
                        .append(text)
                        .append(
                                node.isEditable() && node.getHintText() != null
                                        ? " | hint=" + node.getHintText()
                                        : "")
                        .append(node.isCheckable() ? " | checked=" + node.isChecked() : "");
            }
            return out.toString();
        }
        if ("click".equals(action) && target.matches("#check[0-9]+")) {
            int index = Integer.parseInt(target.substring("#check".length()));
            for (AccessibilityNodeInfo node : nodes)
                if (node.isCheckable() && index-- == 0) {
                    if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        throw new AssertionError("Check refused: " + target);
                    return "clicked " + target;
                }
            throw new AssertionError("No checkable node: " + target);
        }
        if ("click".equals(action)) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.getText() == null || !target.equalsIgnoreCase(node.getText().toString()))
                    continue;
                AccessibilityNodeInfo button = clickable(node);
                if (button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    return "clicked " + target;
            }
            throw new AssertionError("No clickable node: " + target);
        }
        if ("steps".equals(action)) {
            StringBuilder done = new StringBuilder();
            for (String step : target.split("\\|")) {
                String[] kind = step.split(":", 2);
                String stepTarget = kind[1];
                String stepValue = null;
                if ("setText".equals(kind[0])) {
                    int split = stepTarget.indexOf('=');
                    stepValue = stepTarget.substring(split + 1);
                    stepTarget = stepTarget.substring(0, split);
                }
                done.append(once(in, kind[0], stepTarget, stepValue)).append("; ");
            }
            return done.toString();
        }
        if ("longClick".equals(action)) {
            AccessibilityNodeInfo node = byText(nodes, target);
            for (AccessibilityNodeInfo current = node;
                    current != null;
                    current = current.getParent())
                if (current.isLongClickable()
                        && current.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
                    return "long-clicked " + target;
            throw new AssertionError("No long-clickable node: " + target);
        }
        if ("setText".equals(action)) {
            List<AccessibilityNodeInfo> editable = new ArrayList<>();
            for (AccessibilityNodeInfo node : nodes) if (node.isEditable()) editable.add(node);
            AccessibilityNodeInfo field = null;
            if (target.matches("#[0-9]+")) {
                int index = Integer.parseInt(target.substring(1));
                if (index < editable.size()) field = editable.get(index);
            } else {
                for (AccessibilityNodeInfo node : editable)
                    if (target.equals(String.valueOf(node.getText()))
                            || target.equals(String.valueOf(node.getHintText()))) field = node;
            }
            if (field == null) throw new AssertionError("No editable node: " + target);
            Bundle arguments = new Bundle();
            arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
                throw new AssertionError("Set text refused");
            return "set " + target + " to " + value;
        }
        throw new AssertionError("Unknown foreign UI action");
    }

    private static String once(Instrumentation in, String action, String target, String value)
            throws Exception {
        long deadline = SystemClock.uptimeMillis() + 3_000;
        while (true) {
            try {
                return run(in, action, target, value);
            } catch (AssertionError notYetShown) {
                if (SystemClock.uptimeMillis() > deadline) throw notYetShown;
                Thread.sleep(50);
            }
        }
    }

    private static AccessibilityNodeInfo generatedRoot(Instrumentation in) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5_000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = in.getUiAutomation().getRootInActiveWindow();
            if (root != null
                    && String.valueOf(root.getPackageName())
                            .startsWith("dev.smallwright.generated.")) return root;
            Thread.sleep(100);
        }
        throw new AssertionError("Foreground window is not a generated app");
    }

    private static void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        if (node.isVisibleToUser()) out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), out);
    }

    private static AccessibilityNodeInfo byText(List<AccessibilityNodeInfo> nodes, String text) {
        for (AccessibilityNodeInfo node : nodes)
            if (node.getText() != null && text.equalsIgnoreCase(node.getText().toString()))
                return node;
        return null;
    }

    private static AccessibilityNodeInfo clickable(AccessibilityNodeInfo node) {
        for (AccessibilityNodeInfo current = node; current != null; current = current.getParent())
            if (current.isClickable() && current.isEnabled()) return current;
        return null;
    }
}
