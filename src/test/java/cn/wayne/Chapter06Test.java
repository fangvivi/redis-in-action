package cn.wayne;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class Chapter06Test {

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
    void addUpdateContact() {
        Chapter06 chapter06 = new Chapter06();
        jedis.select(2);
        chapter06.addUpdateContact(jedis,"ZhangSan","LiSi");
        chapter06.addUpdateContact(jedis,"ZhangSan","WangWu");
        chapter06.addUpdateContact(jedis,"ZhangSan","ZhaoLiu");
        chapter06.addUpdateContact(jedis,"ZhangSan","Haha");
        chapter06.addUpdateContact(jedis,"ZhangSan","HanMeiMei");
        chapter06.addUpdateContact(jedis,"ZhangSan","LiYang");
    }

    @Test
    void removeContact() {
        Chapter06 chapter06 = new Chapter06();
        jedis.select(2);
        chapter06.removeContact(jedis,"ZhangSan","WangWu");
    }

    @Test
    void fetchAutocompleteList() {
        Chapter06 chapter06 = new Chapter06();
        jedis.select(2);
        List<String> matches = chapter06.fetchAutocompleteList(jedis, "ZhangSan", "H");
        for (String match : matches) {
            log.info(match);
        }
    }
}