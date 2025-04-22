package com.zcq;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class Test {
    public static void main(String[] args) {
        Solution solution = new Solution();
    }
}

class Solution {

    List<TreeNode> res = new LinkedList<>();
    HashMap<String, Integer> map = new HashMap<>();

    public List<TreeNode> findDuplicateSubtrees(TreeNode root) {
        res.clear();
        map.clear();
        getTreeString(root);
        return res;
    }

    public String getTreeString(TreeNode root) {
        if (root == null) {
            return "#";
        }
        String left = getTreeString(root.left);
        String right = getTreeString(root.right);
        String curTreeString = left + "," + right + "," + root.val;
        if (map.getOrDefault(curTreeString, 0) == 1) {
            res.add(root);
        }
        map.merge(curTreeString, 1, Integer::sum);
        return curTreeString;
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