package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final Pattern matcher_ID = Pattern.compile("(\\d+)_(.+)");

    public static void main(String[] args) {
        // キャッシュ定期お掃除
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                try {
                    YamlMapping input = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();

                    JedisPool jedisPool = new JedisPool(input.string("RedisServer"), input.integer("RedisPort"));
                    Jedis jedis = jedisPool.getResource();
                    if (!input.string("RedisPass").isEmpty()){
                        jedis.auth(input.string("RedisPass"));
                    }
                    jedis.keys("nico-vimeo:Cache:*").forEach((key)->{

                        Matcher matcher = matcher_ID.matcher(key);
                        if (matcher.find()){
                            long l = Long.parseLong(matcher.group(1));
                            if (new Date().getTime() - l >= 86400000L){
                                jedis.del(key);
                            }
                        }

                    });
                    jedis.close();
                    jedisPool.close();
                } catch (Exception e){
                    e.printStackTrace();
                }

            }
        }, 0L, 3600000L);

        // 処理受付
        new TCPServer().start();

        // HTTP
        new HTTPServer().start();

    }
}