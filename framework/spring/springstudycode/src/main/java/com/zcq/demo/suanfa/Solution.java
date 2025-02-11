package com.zcq.demo.suanfa;

import java.util.Arrays;

class Solution {
    public static void main(String[] args) {

        System.out.println(Arrays.toString(new Solution().productExceptSelf(new int[]{1, 2, 3, 4})));
        System.out.println(Arrays.toString(new Solution().productExceptSelf(new int[]{-1, 1, 0, -3, 3})));
    }
    public int[] productExceptSelf(int[] nums) {
        int zero = 0;
        int zeroIndex = -1;
        int len = nums.length;
        int[] res = new int[len];
        int[] preSum = new int[len + 1];
        preSum[0] = 1;
        for (int i = 1; i <= len; i++) {
            if (nums[i - 1] != 0) {
                preSum[i] = preSum[i - 1] * nums[i - 1];
            } else {
                preSum[i] = 1;
                zero++;
                zeroIndex = i - 1;
            }
        }
        if (zero > 1) {
            return res;
        } else if (zero != 0) {
            res[zeroIndex] = preSum[len] * preSum[zeroIndex];
            return res;
        }

        for (int i = 0; i < len; i++) {
            res[i] = preSum[i] * (preSum[len] / preSum[i + 1]);
        }
        return res;
    }
}