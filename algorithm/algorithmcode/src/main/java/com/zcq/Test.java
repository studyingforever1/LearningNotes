package com.zcq;

import java.util.Arrays;

public class Test {
    public static void main(String[] args) {
        int[] ints = {3, 4, 2, 1, 9, 7};
        mergerSort(ints, 0, ints.length - 1);
        System.out.println(Arrays.toString(ints));
    }

    public static synchronized void mergerSort(int[] arr, int left, int right) {
        if (left >= right) {
            return;
        }
        int mid = left + (right - left) / 2;
        mergerSort(arr, left, mid);
        mergerSort(arr, mid + 1, right);
        merge(arr, left, mid, right);
    }

    private static void merge(int[] arr, int left, int mid, int right) {
        int[] temp = Arrays.copyOfRange(arr, left, right + 1);
        int i = 0, j = mid + 1 - left, k = left;
        while (i <= mid - left && j <= right - left) {
            arr[k++] = temp[i] < temp[j] ? temp[i++] : temp[j++];
        }
        while (i <= mid - left) {
            arr[k++] = temp[i++];
        }
        while (j <= right - left) {
            arr[k++] = temp[j++];
        }
    }
}