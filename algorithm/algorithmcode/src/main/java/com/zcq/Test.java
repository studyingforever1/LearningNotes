package com.zcq;

import java.util.LinkedList;

public class Test {
    public static void main(String[] args) {
    }
}

class Solution {
    LinkedList<Integer> path = new LinkedList<>();
    int res = 0;
    public int sumNumbers(TreeNode root) {
        traverse(root);
        return res;
    }

    private void traverse(TreeNode root) {
        if (root == null) return;
        path.add(root.val);
        if (root.left == null && root.right == null) {
            int sum = 0;
            for (Integer i : path) {
                sum += sum * 10 + i;
            }
            System.out.println(sum);
            res += sum;
        }
        traverse(root.left);
        traverse(root.right);
        path.removeLast();
    }
}
