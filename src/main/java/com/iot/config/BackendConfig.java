package com.iot.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * desc
 *
 * @author zhiqiang.qian
 * @date 2019-03-17
 */
@Data
@Component
@ConfigurationProperties(prefix = "backend")
public class BackendConfig {
    private String host;

    private String tokenUrl;

    private String tokenUser;

    private String tokenPassword;

    private String deviceStatusUrl;

    private String deviceDataUrl;

    private String deviceUrl;

    private String deviceOfflineUrl;
}
