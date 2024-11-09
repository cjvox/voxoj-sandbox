package com.vox.voxojsandbox.constant;


/**
 * @author voxcode
 * @date 2024/11/9 23:23
 *
 * knife4j 默认模拟数据
 * 一道经典的01背包问题
 */
public interface Mock {

    final static String CODE =  "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner scan = new Scanner(System.in);\n        int N = scan.nextInt();\n        int V = scan.nextInt();\n        int[][] items = new int[N+1][2];\n\n        for (int i = 1; i <= N; i++) {\n            items[i][0] = scan.nextInt();\n            items[i][1] = scan.nextInt();\n        }\n        scan.close();\n\n        int[] dp = new int[V+1];\n\n        for (int i = 1; i <= N; i++) {\n            for (int j = V; j > 0; j--) {\n                if (items[i][0] <= j) {\n                    dp[j] = Math.max(dp[j], dp[j-items[i][0]] + items[i][1]);\n                }\n            }\n        }\n\n        System.out.println(dp[V]);\n    }\n}";

    final static String LANGUAGE = "java";

    final static String INPUT = "[\"4 5 1 2 2 4 3 4 4 5\"]";
}
