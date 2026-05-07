package com.zcq;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class Utf8Utf16Comparison {

    record CharInfo(String label, String text) {}

    public static void main(String[] args) {

        var samples = List.of(
                new CharInfo("ASCII 'A'",                  "A"),
                new CharInfo("拉丁扩展 'é'",               "é"),
                new CharInfo("中文 '你好'",                "你好"),
                new CharInfo("阿拉伯文 'مرحبا'",           "مرحبا"),
                new CharInfo("Emoji '😀'",                 "😀"),
                new CharInfo("Emoji 家庭 '👨‍👩‍👧'",       "👨\u200D👩\u200D👧"),
                new CharInfo("混合 'A你😀'",               "A你😀"),
                new CharInfo("𠀀（CJK扩展B，U+20000）",   "\uD840\uDC00")
        );

        // ── 表头 ──────────────────────────────────────────────────────────────
        String fmt = "%-22s %-10s %-10s %-10s %-10s %-10s %-10s %-10s%n";
        System.out.printf(fmt,
                "描述",
                "字符",
                "码点数",
                "UTF-8字节",
                "UTF-16字节",
                "char数量",
                "代理对?",
                "length()陷阱?"
        );
        System.out.println("─".repeat(100));

        for (var info : samples) {
            analyzeText(info.label(), info.text());
        }

        // ── 详细说明 ─────────────────────────────────────────────────────────
        System.out.println("\n══ 详细规则说明 ══");
        printUtf8Rules();
        printUtf16Rules();
        printSurrogateDemo();
        printLengthTrap();
        printJava17Features();
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void analyzeText(String label, String text) {

        // 码点数（真实字符数）
        long codePointCount = text.codePoints().count();

        // UTF-8 字节数
        int utf8Bytes = text.getBytes(StandardCharsets.UTF_8).length;

        // UTF-16 字节数（不含 BOM，每个 char 占 2 字节）
        int utf16Bytes = text.getBytes(StandardCharsets.UTF_16BE).length;

        // Java char 数量（UTF-16 code unit 数量）
        int charCount = text.length();

        // 是否含代理对
        boolean hasSurrogate = text.chars().anyMatch(
                c -> Character.isSurrogate((char) c)
        );

        // length() 是否与真实字符数不符（即存在"陷阱"）
        boolean lengthTrap = charCount != codePointCount;

        System.out.printf("%-22s %-10s %-10d %-10d %-10d %-10d %-10s %-10s%n",
                label,
                text,
                codePointCount,
                utf8Bytes,
                utf16Bytes,
                charCount,
                hasSurrogate ? "是 ⚠" : "否",
                lengthTrap   ? "是 ⚠" : "否"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void printUtf8Rules() {
        System.out.println("""
            \n【UTF-8 编码规则】
              U+0000   ~ U+007F   → 1 字节  (ASCII 兼容)
              U+0080   ~ U+07FF   → 2 字节  (含 é、ñ 等拉丁扩展)
              U+0800   ~ U+FFFF   → 3 字节  (含大部分 CJK 汉字)
              U+10000  ~ U+10FFFF → 4 字节  (含 Emoji、生僻字)
            """);
    }

    private static void printUtf16Rules() {
        System.out.println("""
            【UTF-16 编码规则】
              U+0000  ~ U+FFFF   → 2 字节（1 个 code unit，基本多文种平面 BMP）
              U+10000 ~ U+10FFFF → 4 字节（2 个 code unit，代理对）
              注：Java 的 char 类型即 UTF-16 code unit（固定 2 字节）
            """);
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void printSurrogateDemo() {
        System.out.println("【代理对（Surrogate Pair）示例：😀 U+1F600】");

        String emoji = "😀";
        int codePoint = emoji.codePointAt(0);

        System.out.printf("  码点：U+%X%n", codePoint);
        System.out.printf("  char[0]（高代理）：U+%X%n", (int) emoji.charAt(0));
        System.out.printf("  char[1]（低代理）：U+%X%n", (int) emoji.charAt(1));

        // 高低代理范围验证
        System.out.printf("  是高代理：%b%n", Character.isHighSurrogate(emoji.charAt(0)));
        System.out.printf("  是低代理：%b%n", Character.isLowSurrogate(emoji.charAt(1)));

        // UTF-8 字节逐个打印
        byte[] utf8 = emoji.getBytes(StandardCharsets.UTF_8);
        System.out.print("  UTF-8 字节：");
        for (byte b : utf8) System.out.printf("0x%02X ", b);
        System.out.println();
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void printLengthTrap() {
        System.out.println("""
            \n【String.length() 的"陷阱"】
              length() 返回的是 UTF-16 code unit 数量，而非真实字符（码点）数。
              对于 BMP 以外的字符（如 Emoji），length() 会比实际字符数多。
            """);

        String s = "A😀B";
        System.out.printf("  字符串：\"%s\"%n", s);
        System.out.printf("  s.length()              = %d  ← 包含代理对，结果为 4%n", s.length());
        System.out.printf("  s.codePointCount(0,len) = %d  ← 真实字符数%n",
                s.codePointCount(0, s.length()));
        System.out.printf("  s.codePoints().count()  = %d  ← 推荐写法（JDK 8+）%n",
                s.codePoints().count());
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void printJava17Features() {
        System.out.println("""
            \n【JDK 17 相关特性】
              1. String 内部存储（JDK 9+ Compact Strings）：
                 - 全 Latin-1 字符 → byte[] (LATIN1，每 char 1 字节，节省内存)
                 - 含非 Latin-1  → byte[] (UTF-16，每 char 2 字节)
              2. Character.toString(int codePoint) → 将码点直接转为字符串
              3. String.chars()       → IntStream of UTF-16 code units
                 String.codePoints() → IntStream of Unicode code points（推荐）
            """);

        // 演示 Compact Strings 内存差异（间接验证）
        String latin  = "Hello";
        String chinese = "你好";
        System.out.printf("  「%s」UTF-8 字节：%d，UTF-16 字节：%d%n",
                latin, latin.getBytes(StandardCharsets.UTF_8).length,
                latin.getBytes(StandardCharsets.UTF_16BE).length);
        System.out.printf("  「%s」UTF-8 字节：%d，UTF-16 字节：%d%n",
                chinese, chinese.getBytes(StandardCharsets.UTF_8).length,
                chinese.getBytes(StandardCharsets.UTF_16BE).length);

        // Character.toString(codePoint)
        int smiley = 0x1F600;
        System.out.printf("%n  Character.toString(0x1F600) = %s%n",
                Character.toString(smiley));

        // codePoints() 流式处理
        System.out.print("  \"A你😀\".codePoints() 码点：");
        "A你😀".codePoints()
                .forEach(cp -> System.out.printf("U+%X ", cp));
        System.out.println();
    }
}
