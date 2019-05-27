package com.iot.config;

import java.nio.charset.Charset;

public class IOTConfig {
    public static final String string_encoding = "UTF-8";

    public static final Charset string_charset = Charset.forName(string_encoding);
    // 客户端发呆5分钟后,服务器主动断开连接
    public static final int tcp_client_idle_minutes = 5;
}