package com.iot.netty.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.iot.config.BackendConfig;
import com.iot.config.IOTConfig;
import com.iot.netty.codec.MsgDecoder;
import com.iot.session.Session;
import com.iot.util.HttpClientHelper;
import com.iot.bo.DataPack;
import com.iot.util.SessionUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class IOTServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(MsgDecoder.class);

    private MsgDecoder decoder;

    private HttpClientHelper httpClientHelper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void setBackendConfig(BackendConfig backendConfig) {
        this.backendConfig = backendConfig;
    }

    private BackendConfig backendConfig;

    public IOTServerHandler(StringRedisTemplate redisTemplate) {
        this.decoder = new MsgDecoder();
        this.httpClientHelper = new HttpClientHelper();
        this.redisTemplate = redisTemplate;
    }

    /**
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            ByteBuf buf = (ByteBuf)msg;
            if (buf.readableBytes() <= 0) {
                return;
            }
            System.out.print(buf.toString(IOTConfig.string_charset));

            if (buf.readableBytes() < 80) {
                log.error("字符长度小于80");
                return;
            }

            byte[] bs = new byte[buf.readableBytes()];
            buf.readBytes(bs);

            DataPack pkg = this.decoder.bytes2PackageData(bs);
            if (!pkg.getVerifyStatus()) {
                log.error("长度验证未通过");
                return;
            }

            pkg.setChannel(ctx.channel());

            SessionUtil.bindSession(new Session(pkg.getMnId()), ctx.channel());

            String token = getToken();
            if (token.isEmpty()) {
                log.error("token获取失败");
                return;
            }

            Map<String, Object> header = new HashMap<String, Object>();
            header.put("token", token);

            if (2000 == Integer.parseInt(pkg.getCnId())) {
                heartbeatPacket(JSON.toJSONString(pkg.getBody()), header);
            } else if (2011 == Integer.parseInt(pkg.getCnId())) {
                saveDeviceData(JSON.toJSONString(pkg.getBody()), header);
            }
        } finally {
            // 抛弃收到的数据
            ReferenceCountUtil.release(msg);
        }
    }

    /**
     * 当Netty由于IO错误或者处理器在处理事件时抛出异常时调用
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

        String token = getToken();
        if (token.isEmpty()) {
            log.error("token获取失败");
            return;
        }

        Map<String, Object> header = new HashMap<String, Object>();
        header.put("token", token);

        deviceOffline(SessionUtil.getSession(ctx.channel()).getMnId(), header);

        System.out.println("异常退出：" + cause.getMessage());
        SessionUtil.unBindSession(ctx.channel());

        // 当出现异常就关闭连接
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("终端连接：" + ctx.channel().id().asLongText());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String token = getToken();
        if (token.isEmpty()) {
            log.error("token获取失败");
            return;
        }

        Map<String, Object> header = new HashMap<String, Object>();
        header.put("token", token);

        deviceOffline(SessionUtil.getSession(ctx.channel()).getMnId(), header);

        System.out.println("终端断开：" + ctx.channel().id().asLongText());
        SessionUtil.unBindSession(ctx.channel());
    }

    @Async
    public String getToken() {
//        if (redisTemplate.hasKey("token")) {
//            return redisTemplate.opsForValue().get("token");
//        }

        Map<String, Object> body = new HashMap<>();
        body.put("username", backendConfig.getTokenUser());
        body.put("password", backendConfig.getTokenPassword());
        String ret = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getTokenUrl(), JSON.toJSONString(body), null);
        System.out.println(ret);
        JSONObject tokenRet = JSONObject.parseObject(ret);
        if (tokenRet.getInteger("code").equals(0)) {
//            redisTemplate.opsForValue().set("token", tokenRet.getString("token"), 3600*9);
            return tokenRet.getString("token");
        } else {
            return null;
        }
    }

    @Async
    public void heartbeatPacket(String data, Map<String, Object> header) {
        String str = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getDeviceStatusUrl(), data, (HashMap<String, Object>) header);
        log.info(str);
    }

    @Async
    public void saveDeviceData(String data, Map<String, Object> header) {
        String str = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getDeviceDataUrl(), data, (HashMap<String, Object>) header);
        log.info(str);
    }

    @Async
    public void getDeviceDetail(String mn, Map<String, Object> header) {
        String str = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getDeviceUrl() + mn, null, (HashMap<String, Object>) header);
        log.info(str);
    }

    @Async
    public void deviceOffline(String mn, Map<String, Object> header) {
        log.info("offline:" + mn);
        String str = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getDeviceOfflineUrl()  + mn, null, (HashMap<String, Object>) header);
        log.info(str);
    }
}
