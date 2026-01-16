package com.zcq;

import java.util.*;

public class Test {
    public static void main(String[] args) {

    }
}

class Solution {
    public int[][] validArrangement(int[][] pairs) {
        Map<Integer, List<Integer>> graph = new HashMap<>();
        for (int[] pair : pairs) {
            int from = pair[0];
            int to = pair[1];
            graph.computeIfAbsent(from, v -> new ArrayList<>()).add(to);
            graph.computeIfAbsent(to, v -> new ArrayList<>());
        }

        List<Integer> res = new ArrayList<>();
        Integer startNode = findStartNode(graph);
        dfs(graph, startNode, res);
        Collections.reverse(res);

        int[][] result = new int[pairs.length][2];
        for (int i = 0; i < res.size() - 1; i++) {
            result[i][0] = res.get(i);
            result[i][1] = res.get(i + 1);
        }
        return result;
    }

    private void dfs(Map<Integer, List<Integer>> graph, Integer startNode, List<Integer> res) {
        while (!graph.get(startNode).isEmpty()) {
            Integer remove = graph.get(startNode).remove(0);
            dfs(graph, remove, res);
        }
        res.add(startNode);
    }

    private Integer findStartNode(Map<Integer, List<Integer>> graph) {
        Map<Integer, Integer> indegree = new HashMap<>();
        Map<Integer, Integer> outdegree = new HashMap<>();

        graph.forEach((from, toList) -> {
            toList.forEach(to -> {
                indegree.put(to, indegree.getOrDefault(to, 0) + 1);
                outdegree.put(from, outdegree.getOrDefault(from, 0) + 1);
            });
        });

        int start = graph.keySet().stream().findFirst().get();
        for (Integer key : graph.keySet()) {
            if (outdegree.getOrDefault(key, 0) - indegree.getOrDefault(key, 0) == 1) {
                start = key;
                break;
            }
        }
        return start;
    }
}

