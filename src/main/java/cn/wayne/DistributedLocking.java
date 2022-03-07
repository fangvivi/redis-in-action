package cn.wayne;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.SetParams;

import java.util.List;
import java.util.UUID;

/**
 * 实现分布式锁
 * @author wayne
 */
@Slf4j
public class DistributedLocking {

    /**
     * 获取锁的默认方法，设置默认的超时时间为10秒
     * @param conn jedis
     * @param lockName 锁的名字
     * @return 锁的标识
     */
    public String acquireLock(Jedis conn, String lockName) {
        return acquireLock(conn, lockName, 10000);
    }


    /**
     * 获取锁
     * @param conn jedis
     * @param lockName 锁名字
     * @param acquireTimeout 获取锁的超时时间，超过这个时间停止获取锁
     * @return 释放锁的标识
     */
    public String acquireLock(Jedis conn, String lockName, long acquireTimeout){
        String identifier = UUID.randomUUID().toString();
        long end = System.currentTimeMillis() + acquireTimeout;
        // 获取锁失败的时候会不断重试，直到获取成功或者到达超时时间为止
        while (System.currentTimeMillis() < end) {
            if(conn.setnx("lock:"+lockName,identifier)==1){
                return identifier;
            }
            try {
                Thread.sleep(1);
            }catch(InterruptedException ie){
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    /**
     * 释放锁
     * @param conn jedis
     * @param lockName 锁的名字
     * @param identifier 释放锁的标识
     * @return true-成功释放锁；false-未能成功释放锁
     */
    public boolean releaseLock(Jedis conn, String lockName, String identifier) {
        String lockKey = "lock:" + lockName;
        while (true){
            conn.watch(lockKey);
            if(identifier.equals(conn.get(lockKey))){
                Transaction trans = conn.multi();
                trans.del(lockKey);
                // 执行失败的话返回null
                List<Object> results = trans.exec();
                if (results == null){
                    continue;
                }
                log.info("释放锁【{}】成功",lockKey);
                return true;
            }
            conn.unwatch();
            break;
        }
        return false;
    }

    /**
     * 获取一个带过期时间的锁
     * @param conn jedis
     * @param lockName 锁名字
     * @param acquireTimeout 获取锁的超时时间，超过这个时间停止获取锁，单位毫秒
     * @param lockTimeout 锁的过期时间，单位毫秒
     * @return 释放锁的标识
     */
    public String acquireLockWithTimeout(Jedis conn,
                                         String lockName,
                                         long acquireTimeout,
                                         long lockTimeout){
        String identifier = UUID.randomUUID().toString();
        String lockKey = "lock:" + lockName;
        // 锁过期时间
        long lockExpire = lockTimeout / 1000;
        long end = System.currentTimeMillis() + acquireTimeout;
        while (true){
            if(System.currentTimeMillis() < end){
                /*
                下面的set方法可以替代注释中的代码
                if(conn.setnx(lockKey, identifier)==1){
                    return identifier;
                }
                // 没有关联过期时间的话设置过期时间
                if(conn.ttl(lockKey)==-1){
                    conn.expire(lockKey, lockExpire);
                }
                */
                SetParams setParams = new SetParams();
                setParams.nx();
                setParams.ex(lockExpire);
                if("OK".equals(conn.set(lockKey, identifier, setParams))){
                    return identifier;
                }
                try {
                    Thread.sleep(1);
                }catch(InterruptedException ie){
                    Thread.currentThread().interrupt();
                }
                break;
            }

        }

        return null;
    }
}
