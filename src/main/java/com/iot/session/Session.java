package com.iot.session;

import lombok.Data;

@Data
public class Session {
    private String mnId;

    public Session(String mnId) {
        this.mnId = mnId;
    }

    @Override
    public String toString() {
        return mnId;
    }
}
