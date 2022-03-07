package cn.wayne;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.ArrayList;
import java.util.List;


/**
 * @author wayne
 */
@Slf4j
public class Chapter06 {

    /**
     * 为指定的用户添加一个最近联系人，最近联系最多有100位
     * @param conn jedis
     * @param user 用户
     * @param contact 要添加的联系
     */
    public void addUpdateContact(Jedis conn, String user, String contact){
        log.info("添加最近联系人，user【{}】, contact【{}】", user, contact);
        String acList = "recent:" + user;
        // 开始事务
        Transaction trans = conn.multi();
        // 如果联系人已经存在的话先删除
        trans.lrem(acList,0, contact);
        // 加到list的头部
        trans.lpush(acList,contact);
        // 只保留100位联系人
        trans.ltrim(acList,0,99);
        // 提交事务
        trans.exec();
    }

    /**
     * 从最近联系人中删除指定的联系人
     * @param conn jedis
     * @param user 用户
     * @param contact 要删除的联系人
     */
    public void removeContact(Jedis conn, String user, String contact){
        log.info("删除最近联系人，user【{}】, contact【{}】", user, contact);
        conn.lrem("recent:" + user,0, contact);
    }

    /**
     * 根据用户数据的内容实现自动补全
     * @param conn jedis
     * @param user 用户
     * @param prefix 自动保全操作的前缀
     * @return 和前缀匹配的最近联系人
     */
    public List<String> fetchAutocompleteList(Jedis conn, String user, String prefix) {
        log.info("自动补全最近联系人，user【{}】, prefix【{}】", user, prefix);
        // 获取整个最近联系人列表
        List<String> candidates = conn.lrange("recent:" + user, 0, -1);
        List<String> matches = new ArrayList<>();
        // 筛选出匹配的
        for (String candidate : candidates) {
            if(candidate.startsWith(prefix)){
                matches.add(candidate);
            }
        }
        return matches;
    }


}
