package com.zcq;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

class Solution {
    LinkedList<Integer> path = new LinkedList<>();
    List<Integer> res;
    boolean[] visited;
    boolean found;

    public List<Integer> grayCode(int n) {
        int m = 1 << n;
        visited = new boolean[m];
        visited[0] = true;
        path.add(0);
        backtrack(m);
        return res;
    }

    private void backtrack(int m) {
        if (path.size() == m) {
            Integer last = path.getLast();
            if (!differsByOneBit(last, 0)) {
                return;
            }

            res = new ArrayList<>(path);
            found = true;
            return;
        }
        if (found) {
            return;
        }
        for (int i = 0; i < m; i++) {
            if (visited[i]) {
                continue;
            }
            Integer last = path.getLast();
            if (!differsByOneBit(last, i)) {
                continue;
            }
            path.add(i);
            visited[i] = true;
            backtrack(m);
            path.removeLast();
            visited[i] = false;
        }
    }

    public static boolean differsByOneBit(int a, int b) {
        int x = a ^ b;
        return x != 0 && (x & (x - 1)) == 0;
    }

}