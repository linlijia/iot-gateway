package com.iot.util;

import org.apache.http.conn.HttpClientConnectionManager;

import javax.annotation.PreDestroy;

public class IdleConnectionEvictor extends Thread {
    private final HttpClientConnectionManager manager;

    private Integer waitTime;

    private volatile boolean shutdown = true;

    public IdleConnectionEvictor(HttpClientConnectionManager manager, Integer waitTime) {
        this.manager = manager;
        this.waitTime = waitTime;
        this.start();
    }

    @Override
    public void run() {
        try {
            if (shutdown) {
                synchronized (this) {
                    wait(waitTime);
                    // 关闭失效的连接
                    manager.closeExpiredConnections();
                }
            }
        } catch (Exception e) {

        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown = false;
        synchronized (this) {
            notifyAll();
        }
    }
}
