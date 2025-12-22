package com.zcq;

public class Test {
    public static void main(String[] args) {

    }
}

class MyCalendar {

    SegmentTree segmentTree;

    public MyCalendar() {
        segmentTree = new SegmentTree(0, Integer.MAX_VALUE, 0);
    }

    public boolean book(int startTime, int endTime) {
        if (segmentTree.query(startTime, endTime - 1) > 0) {
            return false;
        }
        segmentTree.updateRange(startTime, endTime - 1, 1);
        return true;
    }
}

class SegmentTree {
    public static class SegmentNode {
        int l, r;
        int sum;
        SegmentNode left, right;

        boolean hasLazyAssign;
        int lazyAssign;

        public SegmentNode(int l, int r, int sum) {
            this.l = l;
            this.r = r;
            this.sum = sum;
            this.hasLazyAssign = false;
        }
    }

    SegmentNode root;
    int defaultValue;

    public SegmentTree(int start, int end, int defaultValue) {
        this.defaultValue = defaultValue;
        root = new SegmentNode(start, end, defaultValue);
    }

    private void initChildNodeIfNeed(SegmentNode node) {
        if (node.l == node.r) {
            return;
        }
        int mid = node.l + (node.r - node.l) / 2;
        if (node.left == null) {
            node.left = new SegmentNode(node.l, mid, defaultValue);
        }
        if (node.right == null) {
            node.right = new SegmentNode(mid + 1, node.r, defaultValue);
        }
    }

    public void updateRange(int qL, int qR, int value) {
        updateRange(root, qL, qR, value);
    }

    private void updateRange(SegmentNode node, int qL, int qR, int value) {
        if (node.l >= qL && node.r <= qR) {
            node.hasLazyAssign = true;
            node.sum = (node.r - node.l + 1) * value;
            node.lazyAssign = value;
            return;
        }
        initChildNodeIfNeed(node);
        pushDown(node);

        int mid = node.l + (node.r - node.l) / 2;
        if (mid >= qR) {
            updateRange(node.left, qL, qR, value);
        } else if (mid < qL) {
            updateRange(node.right, qL, qR, value);
        } else {
            updateRange(node.left, qL, mid, value);
            updateRange(node.right, mid + 1, qR, value);
        }
        node.sum = node.left.sum + node.right.sum;
    }

    private void pushDown(SegmentNode node) {
        if (!node.hasLazyAssign) {
            return;
        }
        node.left.hasLazyAssign = true;
        node.left.sum = (node.left.r - node.left.l + 1) * node.lazyAssign;
        node.left.lazyAssign = node.lazyAssign;

        node.right.hasLazyAssign = true;
        node.right.sum = (node.right.r - node.right.l + 1) * node.lazyAssign;
        node.right.lazyAssign = node.lazyAssign;

        node.hasLazyAssign = false;
    }

    public int query(int qL, int qR) {
        return query(root, qL, qR);
    }

    private int query(SegmentNode root, int qL, int qR) {
        if (root.l >= qL && root.r <= qR) {
            return root.sum;
        }
        initChildNodeIfNeed(root);
        pushDown(root);
        int mid = root.l + (root.r - root.l) / 2;
        if (mid >= qR) {
            return query(root.left, qL, qR);
        } else if (mid < qL) {
            return query(root.right, qL, qR);
        } else {
            return query(root.left, qL, mid) + query(root.right, mid + 1, qR);
        }
    }

}