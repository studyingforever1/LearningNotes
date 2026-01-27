package com.zcq;

class Solution {
    int res = 0;
    boolean[][] used;
    int[][] d = new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    int count = 0, visitedCount = 0;

    public int uniquePathsIII(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        used = new boolean[m][n];
        int startI = 0, startJ = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1 || grid[i][j] == 0) {
                    count++;
                }
                if (grid[i][j] == 1) {
                    startI = i;
                    startJ = j;
                }
            }
        }

        backtrack(startI, startJ, grid);

        return res;
    }

    private void backtrack(int i, int j, int[][] grid) {

        if (grid[i][j] == 2 && count == visitedCount) {
            res++;
            return;
        }
        used[i][j] = true;
        visitedCount++;
        for (int[] ints : d) {
            int x = ints[0] + i;
            int y = ints[1] + j;

            if (x < 0 || x >= grid.length || y < 0 || y >= grid[0].length) {
                continue;
            }
            if (used[x][y] || grid[x][y] == -1) {
                continue;
            }

            backtrack(x, y, grid);
        }
        used[i][j] = false;
        visitedCount--;
    }
}