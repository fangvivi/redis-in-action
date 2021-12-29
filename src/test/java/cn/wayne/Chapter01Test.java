package cn.wayne;

import static org.junit.jupiter.api.Assertions.*;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import redis.clients.jedis.Jedis;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;


@Slf4j
class Chapter01Test {

    private static Jedis jedis;

    /**
     * 获取redis连接
     */
    @BeforeAll
    static void getRedisConnection() {
        String redisHost = "192.168.40.128";
        int port = 6379;
        try {
            jedis = new Jedis(redisHost, port);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jedis.close();
        }

    }

    /**
     * 发表文章
     */
    @Test
    void postArticleTest() {
        Chapter01 chapter01 = new Chapter01();
        String user = "张三";
        String title = "我是如何成为法外狂徒的？";
        String link = "https://www.article.com/articles/how_to_become_the_outlaw";
        assertEquals(1, Integer.parseInt(chapter01.postArticle(jedis, user, title, link)));
    }

    /**
     * 通过id获取文章
     */
    @Test
    void getArticleByIdTest() {
        Chapter01 chapter01 = new Chapter01();
        String id = "1";
        Map<String, String> articleById = chapter01.getArticleById(jedis, id);
        assertNotNull(articleById);
        for (Map.Entry<String, String> entry : articleById.entrySet()) {
            log.info("【article:"+id+"】 {}:{}", entry.getKey(), entry.getValue());
        }
    }


    /**
     * 投票
     */
    @Test
    void articleVote() {
        Chapter01 chapter01 = new Chapter01();
        chapter01.articleVote(jedis,"lisi","article:1");
    }

    /**
     * 查询指定分组评分最高的文章
     */
    @Test
    void getArticles() {
        Chapter01 chapter01 = new Chapter01();
        List<Map<String, String>> groupArticles
                = chapter01.getGroupArticles(jedis, "new-group", 1);
        chapter01.printArticles(groupArticles);

    }
}