package com.zcq;

import java.util.function.BinaryOperator;

public class Test {
    public static void main(String[] args) {

    }
}

class NumArray {
    SegmentTree segmentTree;

    public NumArray(int[] nums) {
        segmentTree = new SegmentTree(nums, Integer::sum);
    }

    public void update(int index, int val) {
        segmentTree.update(index,val);
    }

    public int sumRange(int left, int right) {
        return segmentTree.query(left, right);
    }
}

class SegmentTree {
    public static class SegmentNode {
        int l, r;
        int mergeValue;
        SegmentNode left, right;

        public SegmentNode(int l, int r, int mergeValue) {
            this.l = l;
            this.r = r;
            this.mergeValue = mergeValue;
        }
    }

    SegmentNode root;
    BinaryOperator<Integer> operator;

    public SegmentTree(int[] nums, BinaryOperator<Integer> operator) {
        this.operator = operator;
        this.root = build(nums, 0, nums.length - 1);
    }

    private SegmentNode build(int[] nums, int left, int right) {
        if (left == right) {
            return new SegmentNode(left, right, nums[left]);
        }
        int mid = left + (right - left) / 2 ;
        SegmentNode leftNode = build(nums, left, mid);
        SegmentNode rightNode = build(nums, mid + 1, right);

        Integer mergeValue = operator.apply(leftNode.mergeValue, rightNode.mergeValue);
        SegmentNode root = new SegmentNode(left, right, mergeValue);
        root.left = leftNode;
        root.right = rightNode;
        return root;
    }

    public void update(int index, int value) {
        update(root, index, value);
    }

    private void update(SegmentNode root, int index, int value) {
        if (root.l == root.r) {
            root.mergeValue = value;
            return;
        }
        int mid = root.l + (root.r - root.l) / 2;
        if (mid >= index) {
            update(root.left, index, value);
        } else {
            update(root.right, index, value);
        }
        root.mergeValue = operator.apply(root.left.mergeValue, root.right.mergeValue);
    }

    public int query(int ql, int qr) {
        return query(root, ql, qr);
    }

    private int query(SegmentNode root, int ql, int qr) {
        if (root.l == ql && root.r == qr) {
            return root.mergeValue;
        }
        int mid = root.l + (root.r - root.l) / 2;
        if (mid >= qr) {
            return query(root.left, ql, qr);
        } else if (mid < ql) {
            return query(root.right, ql, qr);
        } else {
            return operator.apply(query(root.left, ql, mid), query(root.right, mid + 1, qr));
        }
    }
}