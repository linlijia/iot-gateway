package com.iot.netty.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.iot.bo.CommonResponse;
import com.iot.config.BackendConfig;
import com.iot.config.IOTConfig;
import com.iot.netty.codec.MsgDecoder;
import com.iot.session.Session;
import com.iot.util.HttpClientHelper;
import com.iot.bo.DataPack;
import com.iot.util.SessionUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.apache.http.client.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

@Component
public class IOTServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(MsgDecoder.class);
    private final String timeFormat = "yyyyMMddHHmmssSSS";
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
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            ByteBuf buf = (ByteBuf) msg;
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
                heartbeatPacket(pkg, header);
            } else if (2011 == Integer.parseInt(pkg.getCnId())) {
                saveDeviceData(pkg, header);
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
    public void heartbeatPacket(DataPack dataPack, Map<String, Object> header) {
        String str;
        String data = JSON.toJSONString(dataPack.getBody());
        if (data.contains("operationFlag")) {
            str = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getDeviceStatusUrlEx2(), data, (HashMap<String, Object>) header);
        } else if (data.contains("currentStatus")) {
            str = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getDeviceStatusUrlEx(), data, (HashMap<String, Object>) header);
        } else {
            str = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getDeviceStatusUrl(), data, (HashMap<String, Object>) header);
        }
        CommonResponse response = JSON.parseObject(str, CommonResponse.class);
        if (response.getCode() == 0) {
            responseClient(dataPack);
        }
    }

    @Async
    public void saveDeviceData(DataPack dataPack, Map<String, Object> header) {
        String str = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getDeviceDataUrl(), JSON.toJSONString(dataPack.getBody()), (HashMap<String, Object>) header);
        CommonResponse response = JSON.parseObject(str, CommonResponse.class);
        if (response.getCode() == 0) {
            responseClient(dataPack);
        }
    }

    private void responseClient(DataPack dataPack) {
        String response = appendResponseMessage(dataPack);
        final ByteBuf byteBuf = Unpooled.copiedBuffer(response.getBytes());
        dataPack.getChannel().writeAndFlush(byteBuf);
        log.info("返回数据包结束：appendResponseMessage"+response);
    }

    private String appendResponseMessage(DataPack dataPack) {
        StringBuilder sb = new StringBuilder("##");
        String dataSegment = appendDataSegment(dataPack, appendCp(dataPack));
        sb.append(getDataSegmentSize(dataSegment.length())).append(dataSegment).append(getCrc16(dataSegment)).append("\r\n");

        return sb.toString();
    }

    /**
     * 拼接数据部分
     *
     * @param cp cp
     * @return 数据包数据部分
     */
    private String appendDataSegment(DataPack dataPack, String cp) {
        String qn = DateUtils.formatDate(Calendar.getInstance().getTime(), timeFormat);
        return "QN=" + qn + ";ST=" + dataPack.getStId() + ";CN=9012;PW=" + dataPack.getPwId() + ";MN=" + dataPack.getMnId() + ";Flag="
                + 1 + ";CP=&&" + cp + "&&";
    }

    private String appendCp(DataPack dataPack) {
        String dataTime = null;
        try {
            dataTime = (String)dataPack.getBody().get("dataTime");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "DataTime=" + dataTime + ";CN=" + dataPack.getCnId();
    }

    /**
     * 获取数据长度
     *
     * @param length 字符串长度
     * @return string字符串长度，4位，不足4位，高位补0
     */
    private String getDataSegmentSize(int length) {
        StringBuilder size = new StringBuilder();
        if (length >= 100) {
            size.append("0");
        } else if (length >= 10) {
            size.append("00");
        } else if (length >= 0) {
            size.append("000");
        }
        size.append(length);
        return size.toString();
    }

    /**
     * @param dataSegment socket指令的数据段部分
     * @return crc校验码
     */
    private static String getCrc16(String dataSegment) {
        int crc = 0xFFFF;
        int i;
        int j;
        int check;

        for (i = 0; i < dataSegment.length(); i++) {
            crc = (crc >> 8) ^ dataSegment.charAt(i);
            for (j = 0; j < 8; j++) {
                check = crc & 0x0001;
                crc >>= 1;
                if (check == 0x0001) {
                    crc ^= 0xA001;
                }
            }
        }
        String crc16 = Integer.toHexString(crc);
        if (crc16.length() < 4) {
            crc16 = "0" + crc16;
        }
        return crc16;
    }

    @Async
    public void getDeviceDetail(String mn, Map<String, Object> header) {
        String str = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getDeviceUrl() + mn, null, (HashMap<String, Object>) header);
        log.info(str);
    }

    @Async
    public void deviceOffline(String mn, Map<String, Object> header) {
        log.info("offline:" + mn);
        String str = httpClientHelper.postJSON(backendConfig.getHost() + backendConfig.getDeviceOfflineUrl() + mn, null, (HashMap<String, Object>) header);
        log.info(str);
    }
}
