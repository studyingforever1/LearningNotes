package com.zcq;

public class Test {
    public static void main(String[] args) {
//        Solution solution = new Solution();
//        System.out.println(solution.isBalanced("583"));
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