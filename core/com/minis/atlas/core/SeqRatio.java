package com.minis.atlas.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SeqRatio {

    private SeqRatio() {}

    static double ratio(String a, String b) {
        int la = a.length(), lb = b.length();
        int total = la + lb;
        if (total == 0) return 1.0;
        Map<Character, List<Integer>> b2j = new HashMap<>();
        for (int j = 0; j < lb; j++) {
            char c = b.charAt(j);
            List<Integer> l = b2j.get(c);
            if (l == null) {
                l = new ArrayList<>();
                b2j.put(c, l);
            }
            l.add(j);
        }
        List<int[]> blocks = matchingBlocks(a, b, b2j);
        int m = 0;
        for (int[] bl : blocks) m += bl[2];
        return 2.0 * m / total;
    }

    private static List<int[]> matchingBlocks(String a, String b, Map<Character, List<Integer>> b2j) {
        List<int[]> out = new ArrayList<>();
        List<int[]> queue = new ArrayList<>();
        queue.add(new int[]{0, a.length(), 0, b.length()});
        while (!queue.isEmpty()) {
            int[] q = queue.remove(queue.size() - 1);
            int alo = q[0], ahi = q[1], blo = q[2], bhi = q[3];
            int[] x = longestMatch(a, b, b2j, alo, ahi, blo, bhi);
            int i = x[0], j = x[1], k = x[2];
            if (k > 0) {
                out.add(x);
                if (alo < i && blo < j) queue.add(new int[]{alo, i, blo, j});
                if (i + k < ahi && j + k < bhi) queue.add(new int[]{i + k, ahi, j + k, bhi});
            }
        }
        out.add(new int[]{a.length(), b.length(), 0});
        out.sort((p, r) -> Integer.compare(p[1], r[1]));
        return out;
    }

    private static int[] longestMatch(String a, String b, Map<Character, List<Integer>> b2j,
                                      int alo, int ahi, int blo, int bhi) {
        int besti = alo, bestj = blo, bestsize = 0;
        Map<Integer, Integer> j2len = new HashMap<>();
        for (int i = alo; i < ahi; i++) {
            Map<Integer, Integer> newj2len = new HashMap<>();
            List<Integer> js = b2j.get(a.charAt(i));
            if (js != null) {
                for (int j : js) {
                    if (j < blo) continue;
                    if (j >= bhi) break;
                    Integer prev = j2len.get(j - 1);
                    int k = (prev == null ? 0 : prev) + 1;
                    newj2len.put(j, k);
                    if (k > bestsize) {
                        besti = i - k + 1;
                        bestj = j - k + 1;
                        bestsize = k;
                    }
                }
            }
            j2len = newj2len;
        }
        return new int[]{besti, bestj, bestsize};
    }
}
