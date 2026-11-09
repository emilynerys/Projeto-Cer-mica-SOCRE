package br.com.socre.producao.web;

import java.util.*;

// The few values the dashboard charts read, written as JSON for a non-executed <script type="application/json"> block.
final class Json {
    private Json() {}
    static String de(Object valor) {
        var s = new StringBuilder();
        escrever(s, valor);
        return s.toString();
    }
    private static void escrever(StringBuilder s, Object v) {
        switch (v) {
            case null -> s.append("null");
            case Number n -> s.append(n instanceof Double d && (d.isNaN() || d.isInfinite()) ? "null" : n.toString());
            case Boolean b -> s.append(b);
            case Map<?, ?> m -> {
                s.append('{');
                var primeiro = true;
                for (var e : m.entrySet()) {
                    if (!primeiro) s.append(',');
                    primeiro = false;
                    texto(s, String.valueOf(e.getKey())); s.append(':'); escrever(s, e.getValue());
                }
                s.append('}');
            }
            case Collection<?> c -> {
                s.append('[');
                var primeiro = true;
                for (var x : c) { if (!primeiro) s.append(','); primeiro = false; escrever(s, x); }
                s.append(']');
            }
            default -> texto(s, v.toString());
        }
    }
    /** Escapes "<" too, so a value can never close the script block. */
    private static void texto(StringBuilder s, String t) {
        s.append('"');
        for (char c : t.toCharArray()) {
            switch (c) {
                case '"' -> s.append("\\\"");
                case '\\' -> s.append("\\\\");
                case '<' -> s.append("\\u003c");
                case '\n' -> s.append("\\n");
                case '\r' -> s.append("\\r");
                default -> { if (c < 0x20) s.append(String.format("\\u%04x", (int) c)); else s.append(c); }
            }
        }
        s.append('"');
    }
}
