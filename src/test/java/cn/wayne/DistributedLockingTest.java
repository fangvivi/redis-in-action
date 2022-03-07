package cn.wayne;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DistributedLockingTest {

    private static Jedis jedis;

    /**
     * 获取redis连接
     */
    @BeforeAll
    static void getRedisConnection() {
        // String redisHost = "192.168.40.128";
        String redisHost = "192.168.40.110";
        int port = 6379;
        try {
            jedis = new Jedis(redisHost, port);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jedis.close();
        }

    }

    @Test
    void releaseLock() {
        DistributedLocking lock = new DistributedLocking();
        jedis.select(1);
        String lockValue = lock.acquireLockWithTimeout(jedis, "redis", 10000, 100000);
        log.info("lockValue【{}】",lockValue);
    }

    @Test
    void acquireLockWithTimeout() {
        DistributedLocking lock = new DistributedLocking();
        jedis.select(1);
        lock.releaseLock(jedis,"redis","5c8e80e9-ed10-47ff-a01f-5269394d1ada");
    }
}