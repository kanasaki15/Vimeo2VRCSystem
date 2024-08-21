package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

public class TCPServer extends Thread{

    @Override
    public void run() {

        try {
            final ServerSocket socket = new ServerSocket(22222);
            while (true) {
                final Socket sock = socket.accept();
                System.gc();
                new Thread(() -> {
                    try {
                        final InputStream in = sock.getInputStream();
                        final OutputStream out = sock.getOutputStream();
                        byte[] data = new byte[100000000];

                        int readSize = in.read(data);
                        data = Arrays.copyOf(data, readSize);

                        final byte[] bytes = data;
                        if (bytes.length == 0) {
                            sock.close();
                            return;
                        }

                        JsonElement json = new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8), JsonElement.class);
                        //System.out.println(json);

                        if (!json.getAsJsonObject().has("BaseURL")){
                            sock.close();
                            return;
                        }

                        if (!json.getAsJsonObject().has("VideoURL")){
                            sock.close();
                            return;
                        }

                        if (!json.getAsJsonObject().has("AudioURL")){
                            sock.close();
                            return;
                        }

                        boolean no_vrc = false;
                        if (json.getAsJsonObject().has("novrc")){
                            no_vrc = json.getAsJsonObject().get("novrc").getAsBoolean();
                        }

                        String main_m3u8 = "";
                        String sub_m3u8 = "";
                        String videoUrl = "";
                        String audioUrl = "";

                        String BaseURL = json.getAsJsonObject().get("BaseURL").getAsString();
                        //System.out.println("base : "+BaseURL);
                        videoUrl = json.getAsJsonObject().get("VideoURL").getAsString().replaceAll("\\.\\./", BaseURL+"/playlist/av/");
                        //System.out.println("video : "+videoUrl);
                        //String[] baseURL = BaseURL.split("/");
                        audioUrl = json.getAsJsonObject().get("AudioURL").getAsString().replaceAll("\\.\\./", BaseURL+"/playlist/av/");
                        //System.out.println("audio : "+audioUrl);

                        String videoId = new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0];

                        if (no_vrc){
                            main_m3u8 = "#EXTM3U\n" +
                                    "#EXT-X-VERSION:6\n" +
                                    "#EXT-X-INDEPENDENT-SEGMENTS\n" +
                                    "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\""+json.getAsJsonObject().get("Audio").getAsString()+"\",NAME=\"Main Audio\",DEFAULT=YES,URI=\""+audioUrl+"\"\n" +
                                    "#EXT-X-STREAM-INF:BANDWIDTH="+json.getAsJsonObject().get("Bandwidth").getAsLong()+",AVERAGE-BANDWIDTH="+json.getAsJsonObject().get("AverageBandwidth").getAsLong()+",CODECS=\""+json.getAsJsonObject().get("Codecs").getAsString()+"\",RESOLUTION="+json.getAsJsonObject().get("Resolution").getAsString()+",FRAME-RATE="+json.getAsJsonObject().get("FrameRate").getAsString()+",AUDIO=\""+json.getAsJsonObject().get("Audio").getAsString()+"\"\n" +
                                    videoUrl;

                        } else {
                            main_m3u8 = "#EXTM3U\n" +
                                    "#EXT-X-VERSION:6\n" +
                                    "#EXT-X-INDEPENDENT-SEGMENTS\n" +
                                    "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\""+json.getAsJsonObject().get("Audio").getAsString()+"\",NAME=\"Main Audio\",DEFAULT=YES,URI=\""+audioUrl+"\"\n" +
                                    "#EXT-X-STREAM-INF:BANDWIDTH="+json.getAsJsonObject().get("Bandwidth").getAsLong()+",AVERAGE-BANDWIDTH="+json.getAsJsonObject().get("AverageBandwidth").getAsLong()+",CODECS=\""+json.getAsJsonObject().get("Codecs").getAsString()+"\",RESOLUTION="+json.getAsJsonObject().get("Resolution").getAsString()+",FRAME-RATE="+json.getAsJsonObject().get("FrameRate").getAsString()+",AUDIO=\""+json.getAsJsonObject().get("Audio").getAsString()+"\"\n" +
                                    "/"+videoId+"/sub.m3u8";
                            sub_m3u8 = "#EXTM3U\n" +
                                    "#EXT-X-VERSION:6\n" +
                                    "#EXT-X-INDEPENDENT-SEGMENTS\n" +
                                    "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\""+json.getAsJsonObject().get("Audio").getAsString()+"\",NAME=\"Main Audio\",DEFAULT=YES,URI=\""+audioUrl+"\"\n" +
                                    "#EXT-X-STREAM-INF:BANDWIDTH="+json.getAsJsonObject().get("Bandwidth").getAsLong()+",AVERAGE-BANDWIDTH="+json.getAsJsonObject().get("AverageBandwidth").getAsLong()+",CODECS=\""+json.getAsJsonObject().get("Codecs").getAsString()+"\",RESOLUTION="+json.getAsJsonObject().get("Resolution").getAsString()+",FRAME-RATE="+json.getAsJsonObject().get("FrameRate").getAsString()+",AUDIO=\""+json.getAsJsonObject().get("Audio").getAsString()+"\"\n" +
                                    videoUrl;
                        }

                        JsonData jsonData = new JsonData();
                        jsonData.setMain_m3u8(main_m3u8);
                        jsonData.setSub_m3u8(sub_m3u8);


                        YamlMapping input = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();

                        JedisPool jedisPool = new JedisPool(input.string("RedisServer"), input.integer("RedisPort"));
                        Jedis jedis = jedisPool.getResource();
                        if (!input.string("RedisPass").isEmpty()){
                            jedis.auth(input.string("RedisPass"));
                        }

                        jedis.set("nico-vimeo:Cache:"+videoId, new Gson().toJson(jsonData));

                        jedis.close();
                        jedisPool.close();

                        out.write((input.string("BaseURL") + "/" + videoId + "/main.m3u8").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        out.close();
                        in.close();
                        sock.close();
                    } catch (Exception e) {
                        try {
                            sock.close();
                        } catch (Exception ex){
                            // ex.printStackTrace();
                        }
                        throw new RuntimeException(e);
                    }
                }).start();
            }

        } catch (Exception e){
            e.printStackTrace();
        }

    }
}
