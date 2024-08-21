package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTTPServer extends Thread {

    private final Pattern matcher_HTTP = Pattern.compile("(GET|HEAD) (.+) HTTP");
    private final Pattern matcher_HTTPVer = Pattern.compile("HTTP/(\\d)\\.(\\d)");
    private final Pattern matcher_VideoId = Pattern.compile("/(\\d+)_(.+)/");
    private final Pattern matcher_NoVRCUA = Pattern.compile("[uU]ser-[aA]gent: (VLC|vlc)");

    @Override
    public void run() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(22221);
            while (true) {
                Socket sock = socket.accept();
                System.gc();
                new Thread(() -> {
                    try {
                        InputStream in = sock.getInputStream();
                        OutputStream out = sock.getOutputStream();
                        byte[] data = new byte[100000000];

                        int readSize = in.read(data);
                        data = Arrays.copyOf(data, readSize);
                        final String text = new String(data, StandardCharsets.UTF_8);

                        final Matcher matcher = matcher_HTTP.matcher(text);
                        final Matcher matcher1 = matcher_HTTPVer.matcher(text);
                        if (!matcher.find() || !matcher1.find()){

                            out.write(("HTTP/1.1 400 Bad Request\nContent-Type: text/plain; charset=utf-8\n\nbad request").getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            out.close();
                            in.close();
                            sock.close();

                            return;
                        }

                        //System.out.println(text);

                        final String httpVersion = matcher1.group(1) + "." + matcher1.group(2);
                        final String get = matcher.group(1);
                        final String request = matcher.group(2);

                        //System.out.println(request);
                        final Matcher matcher2 = matcher_VideoId.matcher(request);
                        if (!matcher2.find()){

                            if (request.equals("/")){
                                out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n404").getBytes(StandardCharsets.UTF_8));
                                out.flush();
                                out.close();
                                in.close();
                                sock.close();
                                return;
                            }

                            final OkHttpClient client = new OkHttpClient();
                            Request request1 = new Request.Builder()
                                    .url("https://vod-adaptive-ak.vimeocdn.com"+ request)
                                    .build();

                            //System.out.println("https://vod-adaptive-ak.vimeocdn.com"+ request);

                            Response response = client.newCall(request1).execute();
                            if (response.body() != null){
                                System.out.println(response.code());
                                if (response.code() == 200){
                                    out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: "+response.header("Content-Type")+"\n\n").getBytes(StandardCharsets.UTF_8));

                                    if (get.equals("GET")){
                                        out.write(response.body().bytes());
                                    }
                                    out.flush();

                                } else {
                                    out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n404").getBytes(StandardCharsets.UTF_8));
                                    out.flush();
                                }

                                out.close();
                                in.close();
                                sock.close();
                            }
                            response.close();

                            return;
                        }

                        final String VideoID = matcher2.group(1) + "_" + matcher2.group(2);

                        YamlMapping input = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();

                        JedisPool jedisPool = new JedisPool(input.string("RedisServer"), input.integer("RedisPort"));
                        Jedis jedis = jedisPool.getResource();
                        if (!input.string("RedisPass").isEmpty()){
                            jedis.auth(input.string("RedisPass"));
                        }
                        String s = jedis.get("nico-vimeo:Cache:" + VideoID);
                        if (s.isEmpty()){
                            out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                            if (get.equals("GET")){
                                out.write("404".getBytes(StandardCharsets.UTF_8));
                            }
                            out.flush();
                            out.close();
                            in.close();
                            sock.close();

                            return;
                        } else {
                            JsonElement json = new Gson().fromJson(s, JsonElement.class);

                            if (request.endsWith("main.m3u8")){
                                out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: application/vnd.apple.mpegurl; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (get.equals("GET")){

                                    Matcher matcher3 = matcher_NoVRCUA.matcher(text);
                                    if (matcher3.find() && !json.getAsJsonObject().get("sub_m3u8").getAsString().isEmpty()){
                                        out.write(json.getAsJsonObject().get("sub_m3u8").getAsString().getBytes(StandardCharsets.UTF_8));
                                    } else {
                                        out.write(json.getAsJsonObject().get("main_m3u8").getAsString().getBytes(StandardCharsets.UTF_8));
                                    }

                                }
                                out.flush();
                                out.close();
                                in.close();
                                sock.close();

                                return;
                            }
                            if (request.endsWith("sub.m3u8")){
                                out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: application/vnd.apple.mpegurl; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (get.equals("GET")){
                                    out.write(json.getAsJsonObject().get("sub_m3u8").getAsString().getBytes(StandardCharsets.UTF_8));
                                }
                                out.flush();
                                out.close();
                                in.close();
                                sock.close();

                                return;
                            }

                            out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                            if (get.equals("GET")){
                                out.write("404".getBytes(StandardCharsets.UTF_8));
                            }
                            out.flush();
                            out.close();
                            in.close();
                            sock.close();
                        }
                        jedis.close();
                        jedisPool.close();


                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
