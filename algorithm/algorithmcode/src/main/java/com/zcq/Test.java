package com.zcq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Test {
    public static void main(String[] args) {
    }
}

class Solution {
    public List<Integer> findClosestElements(int[] arr, int k, int x) {
        int p = leftBound(arr, x);
        int p1 = p, p2 = p;
        if (p == arr.length) {
            p1 = p - 1;
        }
        List<Integer> res = new ArrayList<>();
        while (p1 >= 0 && p2 < arr.length && res.size() < k) {
            if (p1 == p2) {
                if (arr[p1] == x) {
                    res.add(arr[p1]);
                    p1--;
                    p2++;
                } else {
                    p1--;
                }
            } else {
                if (Math.abs(arr[p1] - x) < Math.abs(arr[p2] - x)) {
                    res.add(arr[p1]);
                    p1--;
                } else if (Math.abs(arr[p1] - x) > Math.abs(arr[p2] - x)) {
                    res.add(arr[p2]);
                    p2++;
                } else {
                    res.add(arr[p1]);
                    p1--;
                }
            }
        }
        if (p1 < 0) {
            while (p2 < arr.length && res.size() < k) {
                res.add(arr[p2]);
                p2++;
            }
        }
        if (p2 >= arr.length) {
            while (p1 >= 0 && res.size() < k) {
                res.add(arr[p1]);
                p1--;
            }
        }
        res.sort(Comparator.naturalOrder());
        return res;
    }

    private int leftBound(int[] arr, int x) {
        int left = 0, right = arr.length - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (arr[mid] == x) {
                right = mid - 1;
            } else if (arr[mid] < x) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return left;
    }
}