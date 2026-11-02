package dev.smallwright.libraries;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Maven version order, as Maven compares versions, and which versions count as stable. */
final class MavenVersion {
    private static final List<String> QUALIFIERS =
            List.of("alpha", "beta", "milestone", "rc", "snapshot", "", "sp");
    private static final Set<String> UNSTABLE =
            Set.of(
                    "alpha",
                    "a",
                    "beta",
                    "b",
                    "milestone",
                    "m",
                    "rc",
                    "cr",
                    "snapshot",
                    "dev",
                    "preview",
                    "pre",
                    "ea",
                    "eap");

    private MavenVersion() {}

    static int compare(String first, String second) {
        List<Object> left = items(first), right = items(second);
        for (int index = 0; index < Math.max(left.size(), right.size()); index++) {
            int order =
                    compareItems(
                            index < left.size() ? left.get(index) : null,
                            index < right.size() ? right.get(index) : null);
            if (order != 0) return order;
        }
        return 0;
    }

    static boolean isStable(String version) {
        for (Object item : items(version))
            if (item instanceof String && UNSTABLE.contains(item)) return false;
        return true;
    }

    private static List<Object> items(String version) {
        List<Object> items = new ArrayList<>();
        String text = version.toLowerCase(Locale.ROOT);
        StringBuilder token = new StringBuilder();
        for (int index = 0; index <= text.length(); index++) {
            char next = index < text.length() ? text.charAt(index) : '.';
            boolean separator = next == '.' || next == '-' || next == '_';
            boolean switches =
                    token.length() > 0
                            && Character.isDigit(next)
                                    != Character.isDigit(token.charAt(token.length() - 1));
            if (separator || switches) {
                if (token.length() > 0) items.add(item(token.toString()));
                token.setLength(0);
            }
            if (!separator) token.append(next);
        }
        while (!items.isEmpty() && isRelease(items.get(items.size() - 1)))
            items.remove(items.size() - 1);
        return items;
    }

    private static Object item(String token) {
        if (Character.isDigit(token.charAt(0))) return new BigInteger(token);
        switch (token) {
            case "a":
                return "alpha";
            case "b":
                return "beta";
            case "m":
                return "milestone";
            case "cr":
                return "rc";
            case "ga":
            case "final":
            case "release":
                return "";
            default:
                return token;
        }
    }

    private static boolean isRelease(Object item) {
        return BigInteger.ZERO.equals(item) || "".equals(item);
    }

    private static int compareItems(Object left, Object right) {
        if (left == null && right == null) return 0;
        if (left == null) return -compareItems(right, null);
        if (left instanceof BigInteger) {
            if (right == null) return ((BigInteger) left).signum();
            if (right instanceof BigInteger)
                return ((BigInteger) left).compareTo((BigInteger) right);
            return 1;
        }
        String qualifier = (String) left;
        if (right instanceof BigInteger) return -1;
        String other = right == null ? "" : (String) right;
        int order = Integer.compare(rank(qualifier), rank(other));
        return order != 0 ? order : qualifier.compareTo(other);
    }

    private static int rank(String qualifier) {
        int rank = QUALIFIERS.indexOf(qualifier);
        return rank < 0 ? QUALIFIERS.size() : rank;
    }
}
