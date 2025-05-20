package com.zcq;

public class Test {
    public static void main(String[] args) {
        Solution solution = new Solution();
        System.out.println(Long.MAX_VALUE);
    }
}

class Solution {
    public int maxProduct(TreeNode root) {
        sum = sum0(root);
        sum(root);
        return (int) (maxProduct % (1e9+7));
    }

    long maxProduct = Integer.MIN_VALUE;
    int sum = 0;

    public int sum(TreeNode root) {
        if (root == null) {
            return 0;
        }
        int left = sum(root.left);
        int right = sum(root.right);

        maxProduct = max(maxProduct, (long) (sum - right) * right, (long) (sum - left) * left);
        return left + right + root.val;
    }

    public int sum0(TreeNode root) {
        if (root == null) {
            return 0;
        }
        int left = sum0(root.left);
        int right = sum0(root.right);
        return left + right + root.val;
    }

    public long max(long a, long b, long c) {
        return Math.max(a, Math.max(b, c));
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