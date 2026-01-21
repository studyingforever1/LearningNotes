package com.zcq;

class Solution {
    boolean isSubIsland = true;
    int[][] dx = new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    public int countSubIslands(int[][] grid1, int[][] grid2) {
        int m = grid2.length;
        int n = grid2[0].length;
        int res = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid1[i][j] == 1) {
                    isSubIsland = true;
                    dfs(grid1, grid2, i, j);
                    if (isSubIsland) {
                        res++;
                    }
                }
            }
        }
        return res;
    }

    private void dfs(int[][] grid1, int[][] grid2, int i, int j) {
        if (i < 0 || j < 0 || i >= grid2.length || j >= grid2[0].length) {
            return;
        }
        if (grid2[i][j] == 0) {
            return;
        }
        if (grid1[i][j] == 0 && grid2[i][j] == 1) {
            isSubIsland = false;
        }
        grid2[i][j] = 0;
        for (int[] ints : dx) {
            int x = ints[0] + i;
            int y = ints[1] + j;
            dfs(grid1, grid2, x, y);
        }
    }
}