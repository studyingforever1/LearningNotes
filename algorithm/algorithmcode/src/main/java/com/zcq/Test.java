package com.zcq;

import java.util.HashMap;
import java.util.LinkedHashSet;

public class Test {
    public static void main(String[] args) {
    }
}

class LFUCache {

    int capacity;
    int minF = 0;
    HashMap<Integer, Integer> kv = new HashMap<>();
    HashMap<Integer, Integer> kf = new HashMap<>();
    HashMap<Integer, LinkedHashSet<Integer>> fk = new HashMap<>();

    public LFUCache(int capacity) {
        this.capacity = capacity;
    }

    public int get(int key) {
        if (kv.containsKey(key)) {
            incrF(key);
            return kv.get(key);
        }
        return -1;
    }

    private void incrF(int key) {
        Integer oldF = kf.get(key);
        kf.put(key, oldF + 1);
        fk.putIfAbsent(oldF + 1, new LinkedHashSet<>());
        fk.get(oldF + 1).add(key);

        fk.get(oldF).remove(key);
        if (fk.get(oldF).isEmpty()) {
            fk.remove(oldF);
            if (minF == oldF) {
                minF = oldF + 1;
            }
        }
    }

    public void put(int key, int value) {
        if (kv.containsKey(key)) {
            incrF(key);
            kv.put(key, value);
            return;
        }
        if (capacity <= kv.size()) {
            removeLastest();
        }
        kv.put(key, value);
        kf.put(key, 1);
        fk.putIfAbsent(1, new LinkedHashSet<>());
        fk.get(1).add(key);
        minF = 1;
    }

    private void removeLastest() {
        Integer removeKey = fk.get(minF).iterator().next();
        kv.remove(removeKey);
        kf.remove(removeKey);
        fk.get(minF).remove(removeKey);
        if (fk.get(minF).isEmpty()) {
            fk.remove(minF);
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