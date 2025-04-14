package com.zcq;

import java.util.Deque;
import java.util.LinkedList;

public class Test {
    public static void main(String[] args) {
        Solution solution = new Solution();
        System.out.println(solution.shortestSubarray(new int[]{2, -1, 2}, 3));
    }
}

class Solution {
    public int shortestSubarray(int[] nums, int k) {
        long[] preSum = new long[nums.length + 1];
        for (int i = 1; i < preSum.length; i++) {
            preSum[i] = preSum[i - 1] + nums[i - 1];
        }
        Queue<Long> queue = new Queue<>();
        int left = 0, right = 0, ans = Integer.MAX_VALUE;
        while (right < preSum.length) {
            long c = preSum[right];
            queue.push(c);
            right++;
            while (left < right && !queue.isEmpty() && c - queue.min() >= k) {
                ans = Math.min(right - left - 1, ans);
                queue.pop();
                left++;
            }
        }
        return ans == Integer.MAX_VALUE ? -1 : ans;
    }

    class Queue<E extends Comparable<E>> {
        Deque<E> minQueue = new LinkedList<>();
        Deque<E> maxQueue = new LinkedList<>();
        Deque<E> queue = new LinkedList<>();

        public void push(E x) {
            while (!minQueue.isEmpty() && x.compareTo(minQueue.getLast()) < 0) {
                minQueue.removeLast();
            }
            minQueue.addLast(x);
            while (!maxQueue.isEmpty() && x.compareTo(maxQueue.getLast()) > 0) {
                maxQueue.removeLast();
            }
            maxQueue.addLast(x);
            queue.addLast(x);
        }

        public void pop() {
            E removeFirst = queue.removeFirst();
            if (removeFirst.equals(minQueue.getFirst())) {
                minQueue.removeFirst();
            }
            if (removeFirst.equals(maxQueue.getFirst())) {
                maxQueue.removeFirst();
            }
        }

        public E min() {
            return minQueue.getFirst();
        }

        public E max() {
            return maxQueue.getFirst();
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }
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