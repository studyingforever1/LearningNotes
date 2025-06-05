package com.zcq;

import java.util.*;

public class Test {
    public static void main(String[] args) {
    }
}


class Solution {
    HashMap<Integer, TreeNode> parentMap = new HashMap<>();

    public List<Integer> distanceK(TreeNode root, TreeNode target, int k) {
        traverse(root, null);
        List<Integer> ans = new ArrayList<>();

        Queue<TreeNode> queue = new LinkedList<>();
        Set<Integer> set = new HashSet<>();
        queue.offer(target);
        set.add(target.val);
        int distance = 0;
        while (!queue.isEmpty()) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                TreeNode node = queue.poll();
                if (distance == k) {
                    ans.add(node.val);
                }
                if (node.left != null && !set.contains(node.left.val)) {
                    set.add(node.left.val);
                    queue.offer(node.left);
                }
                if (node.right != null && !set.contains(node.right.val)) {
                    set.add(node.right.val);
                    queue.offer(node.right);
                }
                TreeNode parent = parentMap.get(node.val);
                if (parent != null && !set.contains(parent.val)) {
                    set.add(parent.val);
                    queue.offer(parent);
                }
            }
            distance++;
        }
        return ans;
    }

    private void traverse(TreeNode root, TreeNode parent) {
        if (root == null) {
            return;
        }
        parentMap.put(root.val, parent);
        traverse(root.left, root);
        traverse(root.right, root);
    }
}
//class Solution {
//    public long countOfSubstrings(String word, int k) {
//        long ans = 0, len = word.length();
//        int left = 0, right = 0, valid = 0, yuanCount = 0;
//        int[] arr = new int[26];
//        HashSet<Character> yuan = new HashSet<>();
//        add(yuan);
//        while (right < len) {
//            char c = word.charAt(right);
//            arr[c - 'a']++;
//            if (yuan.contains(c)) {
//                yuanCount++;
//                if (arr[c - 'a'] == 1) {
//                    valid++;
//                }
//            }
//            right++;
//            while (valid == yuan.size() && right - left - yuanCount >= k) {
//                if (right - left - yuanCount == k) {
//                    ans++;
//                }
//                char d = word.charAt(left);
//                arr[d - 'a']--;
//                if (yuan.contains(d)) {
//                    yuanCount--;
//                    if (arr[d - 'a'] == 0) {
//                        valid--;
//                    }
//                }
//                left++;
//            }
//        }
//        return ans;
//    }
//
//    private void add(HashSet<Character> yuan) {
//        yuan.add('a');
//        yuan.add('e');
//        yuan.add('i');
//        yuan.add('o');
//        yuan.add('u');
//    }
//}