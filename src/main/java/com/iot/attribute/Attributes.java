package com.iot.attribute;

import com.iot.session.Session;
import io.netty.util.AttributeKey;

/**
 * desc
 *
 * @author zhiqiang.qian
 * @date 2019-03-12
 */
public interface Attributes {
    AttributeKey<Session> SESSION = AttributeKey.newInstance("session");
}
