package com.zcq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Solution {
    List<List<Integer>> result = new ArrayList<>();
    List<Integer> list = new ArrayList<>();

    public List<List<Integer>> combinationSum2(int[] candidates, int target) {
        Arrays.sort(candidates);
        backtrack(candidates, 0, target);
        return result;
    }

    private void backtrack(int[] nums, int start, int target) {
        Integer sum = list.stream().reduce(0, Integer::sum);
        if (sum.equals(target)) {
            result.add(new ArrayList<>(list));
        }
        if (sum > target) {
            return;
        }
        for (int i = start; i < nums.length; i++) {
            if (i > start && nums[i] == nums[i - 1]) {
                continue;
            }
            list.add(nums[i]);
            backtrack(nums, i + 1, target);
            list.remove(list.size() - 1);
        }
    }
}

