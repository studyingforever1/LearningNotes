package com.zcq;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于整数运算的令牌桶算法实现
 * 避免使用浮点数带来的精度问题
 */
public class PreciseTokenBucket {
    // 桶的容量
    private final long capacity;
    // 每毫秒生成的令牌数(放大1000倍，避免小数)
    private final long tokensPerMsScaled;
    // 当前令牌数量
    private long tokens;
    // 最后填充令牌的时间(毫秒)
    private long lastRefillTimestamp;
    // 用于线程安全的锁
    private final ReentrantLock lock;
    // 缩放因子，用于将浮点数转换为整数
    private static final long SCALE_FACTOR = 1000;

    /**
     * 构造函数
     * @param capacity 桶的最大容量
     * @param refillRate 令牌生成速率(个/秒)
     */
    public PreciseTokenBucket(long capacity, double refillRate) {
        this.capacity = capacity;
        // 将每秒生成的令牌数转换为每毫秒生成的令牌数，并放大SCALE_FACTOR倍
        this.tokensPerMsScaled = (long)(refillRate * SCALE_FACTOR / 1000);
        this.tokens = capacity; // 初始时桶是满的
        this.lastRefillTimestamp = System.currentTimeMillis();
        this.lock = new ReentrantLock();
    }

    /**
     * 尝试获取令牌
     * @param numTokens 需要获取的令牌数量
     * @return 是否成功获取令牌
     */
    public boolean tryConsume(long numTokens) {
        // 如果请求的令牌数超过桶的容量，直接拒绝
        if (numTokens > capacity) {
            return false;
        }

        lock.lock();
        try {
            // 先填充令牌
            refill();

            // 检查是否有足够的令牌
            if (tokens >= numTokens) {
                tokens -= numTokens;
                return true;
            }

            // 令牌不足
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 填充令牌
     * 根据上次填充时间到现在的时间差，计算应该生成的令牌数
     */
    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedMs = now - lastRefillTimestamp;

        if (elapsedMs <= 0) {
            return;
        }

        // 计算应该生成的令牌数，使用整数运算
        // 这里相当于 (elapsedMs * tokensPerMsScaled) / SCALE_FACTOR
        long newTokens = (elapsedMs * tokensPerMsScaled) / SCALE_FACTOR;

        if (newTokens > 0) {
            // 增加令牌，但不超过桶的容量
            tokens = Math.min(capacity, tokens + newTokens);
            // 更新最后填充时间
            lastRefillTimestamp = now;
        }
    }

    /**
     * 获取当前桶中的令牌数量
     * @return 当前令牌数量
     */
    public long getCurrentTokens() {
        lock.lock();
        try {
            refill();
            return tokens;
        } finally {
            lock.unlock();
        }
    }

    // 测试方法
    public static void main(String[] args) throws InterruptedException {
        // 创建一个容量为10，每秒生成2个令牌的令牌桶
        PreciseTokenBucket tokenBucket = new PreciseTokenBucket(10, 2);

        System.out.println("初始令牌数: " + tokenBucket.getCurrentTokens());

        // 测试消费令牌
        System.out.println("尝试消费5个令牌: " + (tokenBucket.tryConsume(5) ? "成功" : "失败"));
        System.out.println("剩余令牌数: " + tokenBucket.getCurrentTokens());

        System.out.println("尝试消费6个令牌: " + (tokenBucket.tryConsume(6) ? "成功" : "失败"));
        System.out.println("剩余令牌数: " + tokenBucket.getCurrentTokens());

        // 等待2秒，让令牌生成
        System.out.println("等待2秒...");
        TimeUnit.SECONDS.sleep(2);

        System.out.println("等待后令牌数: " + tokenBucket.getCurrentTokens());
        System.out.println("尝试消费5个令牌: " + (tokenBucket.tryConsume(5) ? "成功" : "失败"));
        System.out.println("剩余令牌数: " + tokenBucket.getCurrentTokens());
    }
}
    