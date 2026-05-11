package com.zcq;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class Test {
    public static void main(String[] args) {
        int[] ints = {3, 4, 2, 1, 9, 7};
        mergeSort(ints, 0, ints.length - 1);
        System.out.println(Arrays.toString(ints));

        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(16,0.75f,true);
    }

    private static void mergeSort(int[] ints, int l, int r) {
        if (l >= r) {
            return;
        }
        int mid = l + (r - l) / 2;
        mergeSort(ints, l, mid);
        mergeSort(ints, mid + 1, r);
        merge(ints, l, r, mid);
    }

    private static void merge(int[] ints, int l, int r, int mid) {
        int[] temp = Arrays.copyOfRange(ints, l, r + 1);
        int i = 0, j = mid - l + 1, k = l;
        while (i <= mid - l && j <= r - l) {
            ints[k++] = temp[i] < temp[j] ? temp[i++] : temp[j++];
        }
        while (i <= mid - l) {
            ints[k++] = temp[i++];
        }
        while (j <= r - l) {
            ints[k++] = temp[j++];
        }

    }


}
