package com.zcq;

public class Test {
    public static void main(String[] args) {

    }
}

class MyCalendarThree {

    SegmentTree segmentTree;

    public MyCalendarThree() {
        segmentTree = new SegmentTree(0, 1000000000, 0);
    }

    public int book(int startTime, int endTime) {
        segmentTree.addRange(startTime, endTime - 1, 1);
        return segmentTree.query(0, 100_000_000);
    }
}

class SegmentTree {
    public static class SegmentNode {
        private int l, r;
        private SegmentNode left, right;
        private int max;

        private boolean hasLazyAdd;
        private int lazyAdd;

        public SegmentNode(int l, int r, int max) {
            this.l = l;
            this.r = r;
            this.max = max;
        }
    }

    SegmentNode root;
    int defaultValue;

    public SegmentTree(int l, int r, int defaultValue) {
        root = new SegmentNode(l, r, defaultValue);
    }

    public void addRange(int qL, int qR, int value) {
        addRange(root, qL, qR, value);
    }

    private void addRange(SegmentNode root, int qL, int qR, int value) {
        if (root.l >= qL && root.r <= qR) {
            root.hasLazyAdd = true;
            root.lazyAdd += value;
            root.max += value;
            return;
        }
        buildChildIfNeed(root);
        pushDown(root);

        int mid = root.l + (root.r - root.l) / 2;
        if (qR <= mid) {
            addRange(root.left, qL, qR, value);
        } else if (qL > mid) {
            addRange(root.right, qL, qR, value);
        } else {
            addRange(root.left, qL, mid, value);
            addRange(root.right, mid + 1, qR, value);
        }
        root.max = Math.max(root.left.max, root.right.max);

    }

    private void pushDown(SegmentNode root) {
        if (!root.hasLazyAdd) {
            return;
        }

        root.left.hasLazyAdd = true;
        root.left.lazyAdd += root.lazyAdd;
        root.left.max += root.lazyAdd;

        root.right.hasLazyAdd = true;
        root.right.lazyAdd += root.lazyAdd;
        root.right.max += root.lazyAdd;

        root.hasLazyAdd = false;
        root.lazyAdd = 0;

    }

    private void buildChildIfNeed(SegmentNode root) {
        if (root.l == root.r) {
            return;
        }
        int mid = root.l + (root.r - root.l) / 2;
        if (root.left == null) {
            root.left = new SegmentNode(root.l, mid, defaultValue);
        }
        if (root.right == null) {
            root.right = new SegmentNode(mid + 1, root.r, defaultValue);
        }
    }

    public int query(int qL, int qR) {
        return query(root, qL, qR);
    }

    private int query(SegmentNode root, int qL, int qR) {
        if (qL <= root.l && root.r <= qR) {
            return root.max;
        }

        buildChildIfNeed(root);
        pushDown(root);

        int mid = root.l + (root.r - root.l) / 2;
        if (qR <= mid) {
            return query(root.left, qL, qR);
        } else if (qL > mid) {
            return query(root.right, qL, qR);
        } else {
            return Math.max(query(root.left, qL, mid), query(root.right, mid + 1, qR));
        }
    }

}

