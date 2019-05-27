package com.iot.netty.handler;

import com.alibaba.fastjson.JSONObject;
import com.iot.util.IOTUtil;
import com.iot.util.SessionUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;

/**
 * desc
 *
 * @author zhiqiang.qian
 * @date 2019-03-17
 */
@Component
public class MqReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqReceiver.class);

    private Thread listenThread;

    public void start(StringRedisTemplate redisTemplate) {
        listenThread = new Thread() {
            @Override
            public void run()  {
                try {
                    while (true) {
                        Object huanbao = redisTemplate.opsForList().rightPop("huanbao");
                        if (huanbao != null) {
                            JSONObject js = JSONObject.parseObject(huanbao.toString());
                            for (Map.Entry<String, Object> entry : js.entrySet()) {
                                System.out.println(entry.getKey() + ":" + entry.getValue());

                                String mn = entry.getKey();
                                String operate = entry.getValue().toString();
                                Channel channel = SessionUtil.getChannel(mn);
                                if (channel != null && SessionUtil.hasLogin(channel)) {
                                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                                    String operate_up = "QN=" + simpleDateFormat.format(Calendar.getInstance().getTime()) + ";ST=22;CN=" + operate + ";PW=123456;MN="+ mn +";Flag=1;CP=&&&&";
                                    String info_length = String.format("%04d", operate_up.length());
                                    String operate_up_info = "##" + info_length + operate_up + IOTUtil.getCRC(operate_up.getBytes()) + "\r\n";
                                    System.out.println(operate_up_info);

                                    final ByteBuf byteBuf = Unpooled.copiedBuffer(operate_up_info.getBytes());
                                    channel.writeAndFlush(byteBuf);
                                } else {
                                    System.out.println("[" + mn + "]不在线，" + operate + "操作失败！");
                                    LOGGER.error("[" + mn + "]不在线，" + operate + "操作失败！");
                                }
                            }

                        }
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    LOGGER.error("[出现异常] 设备操作");
                    e.printStackTrace();
                }
            }
        };
        listenThread.setDaemon(true);
        listenThread.start();
    }
}
