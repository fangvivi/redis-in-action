package cn.wayne;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ZParams;

import java.util.*;


/**
 * @author wayne
 */
@Slf4j
public class Chapter01 {

    /** 一周的秒数 */
    private static final long ONE_WEEK_IN_SECONDS = 7 * 86400;
    /** 每票对应的评分 */
    private static final int VOTE_SCORE = 432;
    /** 每页的文章数量 */
    private static final int ARTICLES_PER_PAGE = 25;


    /**
     * 为文章增加1票<br/>
     * zset【time:】获取文章的score，判断是否可以继续投票<br/>
     * 投票成功<br/>
     * zset【score:】文章的score增加1票对应的分数分数<br/>
     * hash【article:articleId】的field【votes】对应的value增加1票
     * @param conn redis连接
     * @param user 投票的用户id
     * @param article 被投票的文章
     */
    public void articleVote(Jedis conn, String user, String article){
        // 一周前的时间点
        long cutoff = (System.currentTimeMillis() / 1000)- ONE_WEEK_IN_SECONDS;
        // 文章发表已经超过一周，无法继续投票
        if(conn.zscore("time:",article) < cutoff){
            return;
        }
        // 文章的名称格式【article:100408】，冒号后面是文章的id
        String articleId = article.substring(article.indexOf(":") + 1);
        // 投票成功
        if(conn.sadd("voted:" + articleId, user) == 1){
            // 文章的评分增加
            conn.zincrby("score:",VOTE_SCORE, article);
            // 文章的票数增加
            conn.hincrBy("article:"+articleId,"votes",1);
        }
        log.info("用户【{}】为文章【{}】投了一票",user, article);
    }

    /**
     * 文章发布功能<br/>
     * 新发布的文章，默认有一票<br/>
     * set【voted:articleId】把文章作者保存为第一个投票者，设定一周后过期<br/>
     * string【article:】使用自增1的方式记录文章的id
     * hash【article:articleId】保存文章的相关信息<br/>
     * zset【time:】保存文章的发布时间<br/>
     * zset【score:】保存文章的初始评分
     * @param conn redis连接
     * @param user 发布文章的用户
     * @param title 文章标题
     * @param link 文章链接
     * @return 文章的id
     */
    public String postArticle(Jedis conn, String user,  String title, String link){
        // 记录文章的id
        String articleId = String.valueOf(conn.incr("article:"));
        String votedKey = "voted:" + articleId;
        conn.sadd(votedKey, user);
        // 一周以后文章无法被投票，保存投票用户的set自动删除
        conn.expire(votedKey, ONE_WEEK_IN_SECONDS);

        long now = System.currentTimeMillis() / 1000;

        String article = "article:" + articleId;
        Map<String, String> articleMap = new HashMap<>(6);
        articleMap.put("title", title);
        articleMap.put("link", link);
        articleMap.put("user",user);
        articleMap.put("time",String.valueOf(now));
        articleMap.put("votes","1");
        conn.hset(article,articleMap);
        conn.zadd("time:", now, article);
        // 新文章默认有一票
        conn.zadd("score:",now+VOTE_SCORE, article);
        log.info("用户【{}】发表了文章【{}】",user, article);
        return articleId;
    }

    /**
     * 按照页数，获取最新发布或者评分最高的文章<br/>
     * 使用zset的zrevrange命令获取从大到小的数据
     * @param conn redis连接
     * @param page 要展示的页数
     * @param orderSetKey time or score
     * @return 满足条件的文章
     */
    public List<Map<String, String>> getArticles(Jedis conn, int page, String orderSetKey){
        // 因为文章的id在第一页从0开始，且每次递增都会加一。所以每页的第一篇文章的id就是前几页的文章总数
        int start = (page - 1) * ARTICLES_PER_PAGE;
        int end = start + ARTICLES_PER_PAGE -1;
        // ordered set默认存储顺序是按照score从小到大，这里从大到小获取元素
        Set<String> ids = conn.zrevrange(orderSetKey, start, end);
        List<Map<String, String>> articleList = new ArrayList<>();
        for (String id : ids) {
            // 查询文章的相关信息
            Map<String, String> articleData = conn.hgetAll(id);
            // 保存文章的id
            articleData.put("id",id);
            articleList.add(articleData);
        }
        return articleList;
    }

    /**
     * 按照评分排名，获取指定页数的文章<br/>
     * 获取zset【score:】中的数据
     * @param conn redis连接
     * @param page 要展示的页数
     * @return 满足条件的文章
     */
    public List<Map<String, String>> getArticlesByScore(Jedis conn, int page){
       return getArticles(conn, page, "score:");
    }

    /**
     * 根据id获取文章<br/>
     * 获取hash【article:acticleId】的数据
     * @param conn redis连接
     * @param id 文章id
     * @return 要查询的文章
     */
    public Map<String, String> getArticleById(Jedis conn, String id){
        return conn.hgetAll("article:" + id);
    }

    /**
     * 给指定的文章分组<br/>
     * 把文章id存入指定的group set中<br/>
     * 把article存入多个set【group:groupId】中
     * @param conn redis连接
     * @param articleId 文章id
     * @param groupIds 为文章指定的分组
     */
    public void addGroups(Jedis conn, String articleId, String[] groupIds){
        String article = "article:" + articleId;
        for (String groupId : groupIds) {
            conn.sadd("group:"+groupId, article);
        }
    }

    /**
     * 根据评分或者发布时间对群组文章进行分页和排序，把一个群组里所有的文章有序地存储到一个有序集合里面<br/>
     * 如果查询的结果不存在，就先创建一个相应的zset作为缓存，60秒后过期，创建完之后再进行查询<br/>
     * 使用【score:】或者【time:】和【group:groupId】取交集，可以得到有序的组元素<br/>
     *【group:groupId】本身是个set，是无序的
     * @param conn redis连接
     * @param groupId 分组id
     * @param page 要展示的页数
     * @param orderSetKey 时间 or 评分
     * @return 满足条件的文章
     */
    public List<Map<String, String>> getGroupArticles(Jedis conn, String groupId, int page, String orderSetKey){
        // 缓存的key
        String key = orderSetKey + groupId;
        log.info("缓存的key为【{}】",key);
        if(!conn.exists(key)){
            // 取交集，选择score值比较大的元素
            ZParams param = new ZParams().aggregate(ZParams.Aggregate.MAX);
            // 生成缓存，等到一个按照评分或者时间排序的群组文章，减小计算的压力
            conn.zinterstore(key, param, "group:"+ groupId, orderSetKey);
            // 缓存60秒之后删除
            conn.expire(key, 60L);
        }
        return getArticles(conn, page, key);
    }

    /**
     * 按照指定的顺序获取分组的文章
     * @param conn redis连接
     * @param groupId 分组id
     * @param page 页数
     * @return
     */
    public List<Map<String, String>> getGroupArticles(Jedis conn, String groupId, int page){
        return getGroupArticles(conn,groupId,page,"score:");
    }

    /**
     * 输出查询出的文章信息（hash）
     * @param articles 文章的列表
     */
    public void printArticles(List<Map<String, String>> articles){
        for (Map<String, String> article : articles) {
            String id = article.get("id");
            Set<Map.Entry<String, String>> entries = article.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                if("id".equals(entry.getKey())){
                    continue;
                }
                log.info("【article:"+id+"】 {}:{}", entry.getKey(), entry.getValue());
            }
        }
    }

    public void run(){
        String redisHost = "192.168.40.128";
        int port = 6379;
        Jedis jedis = new Jedis(redisHost, port);
        String user = "username";
        String title = "A title";
        String link = "https://www.google.com";
        String articleId = postArticle(jedis, user, title, link);
        log.info("We posted a new article with id: {}", articleId);
        log.info("Its HASH looks like:");
        Map<String, String> articleById = getArticleById(jedis, articleId);
        for (Map.Entry<String, String> entry : articleById.entrySet()) {
            log.info("【article:"+articleId+"】 {}:{}", entry.getKey(), entry.getValue());
        }

        log.info("---------------------------------------------");

        articleVote(jedis,"other_user","article:"+articleId);
        String votes = jedis.hget("article:" + articleId, "votes");
        log.info("We voted for the article, it now has votes: {}", votes);
        assert Integer.parseInt(votes) > 1;
        log.info("The currently highest-scoring articles are:");
        List<Map<String, String>> articles = getArticlesByScore(jedis, 1);
        printArticles(articles);
        assert articles.size() >=1;

        addGroups(jedis,articleId, new String[]{"new-group"});
        log.info("We added the article to a new group, other articles include:");
        articles = getGroupArticles(jedis, "new-group", 1);
        printArticles(articles);
        assert articles.size() >=1;

    }

    public static void main(String[] args) {
        new Chapter01().run();
    }


}
