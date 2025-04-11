package com.zcq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Test {
    public static void main(String[] args) {
        Solution solution = new Solution();
        System.out.println(Arrays.toString(solution.maxSlidingWindow(new int[]{2, 3, 4, 5, 1}, 4)));
    }
}

class Solution {

    class MonQueue {

        LinkedList<Integer> linkedList = new LinkedList<>();

        public int pop(int x) {
            if (x == linkedList.peekFirst()) {
                return linkedList.pollFirst();
            }
            return -1;
        }

        public void push(int x) {
            while (!linkedList.isEmpty() && linkedList.peekLast() < x) {
                linkedList.pollLast();
            }
            linkedList.addLast(x);
        }

        public int max() {
            return linkedList.peekFirst();
        }
    }

    public int[] maxSlidingWindow(int[] nums, int k) {
        MonQueue queue = new MonQueue();
        int i = 0;
        List<Integer> ans = new ArrayList<>();
        while (i < nums.length) {
            if (i < k - 1) {
                queue.push(nums[i]);
            } else {
                queue.push(nums[i]);
                ans.add(queue.max());
                queue.pop(nums[i - k + 1]);
            }
            i++;
        }
        int[] ansArr = new int[ans.size()];
        for (int j = 0; j < ans.size(); j++) {
            ansArr[j] = ans.get(j);
        }
        return ansArr;
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