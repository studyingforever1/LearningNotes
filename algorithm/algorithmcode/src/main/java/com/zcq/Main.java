//package com.zcq;
//
//import java.util.HashSet;
//import java.util.Set;
//
//public class Main {
//    public static void main(String[] args) {
//        //aaabb 3
//        Solution solution = new Solution();
//        System.out.println(solution.longestSubstring("ababbc", 2));
//    }
//}
//
//class Solution {
//    public int longestSubstring(String s, int k) {
//        int left = 0, right = 0, ans = 0;
//        int[] arr = new int[26];
//        int[] show = new int[26];
//        Set<Character> unK = new HashSet<>(26);
//        for (int i = 0; i < s.length(); i++) {
//            arr[s.charAt(i) - 'a']++;
//        }
//        for (int i = 0; i < arr.length; i++) {
//            if (arr[i] > 0 && arr[i] < k) {
//                unK.add((char) ('a' + i));
//            }
//        }
//        while (right < s.length()) {
//            char c = s.charAt(right);
//            right++;
//            if (!unK.contains(c)) {
//                show[c - 'a']++;
//            }
//            while (unK.contains(c) && left < right) {
//                if (isMatch(show, k)) {
//                    ans = Math.max(right - left - 1, ans);
//                }
//                char d = s.charAt(left);
//                left++;
//                show[d - 'a']--;
//            }
//        }
//        if (isMatch(show, k)) {
//            ans = Math.max(right - left, ans);
//        }
//
//        return ans;
//    }
//
//    private boolean isMatch(int[] show, int k) {
//        for (int n : show) {
//            if (n > 0 && n < k) {
//                return false;
//            }
//        }
//        return true;
//    }
//}