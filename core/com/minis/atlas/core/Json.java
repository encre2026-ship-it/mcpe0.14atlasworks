package com.minis.atlas.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        Object v = j.value();
        return v;
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\uFEFF') i++;
            else break;
        }
    }

    private Object value() {
        ws();
        if (i >= s.length()) return null;
        char c = s.charAt(i);
        switch (c) {
            case '{': return obj();
            case '[': return arr();
            case '"': return str();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return num();
        }
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (i < s.length()) {
            ws();
            if (s.charAt(i) != '"') break;
            String k = str();
            ws();
            if (i < s.length() && s.charAt(i) == ':') i++;
            m.put(k, value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
            if (i < s.length() && s.charAt(i) == '}') { i++; break; }
            break;
        }
        return m;
    }

    private List<Object> arr() {
        List<Object> a = new ArrayList<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return a; }
        while (i < s.length()) {
            a.add(value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
            if (i < s.length() && s.charAt(i) == ']') { i++; break; }
            break;
        }
        return a;
    }

    private String str() {
        StringBuilder b = new StringBuilder();
        i++;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\' && i < s.length()) {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        if (i + 4 <= s.length()) {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        break;
                    default: b.append(e);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private Double num() {
        int st = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++;
            else break;
        }
        if (i == st) { i++; return 0.0; }
        try {
            return Double.parseDouble(s.substring(st, i));
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static String esc(String v) {
        StringBuilder b = new StringBuilder(v.length() + 8);
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.toString();
    }

    public static double d(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return 0;
    }
}
