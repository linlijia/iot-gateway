package com.iot.bo;

import com.alibaba.fastjson.annotation.JSONField;
import io.netty.channel.Channel;
import lombok.Data;

import java.util.Map;

@Data
public class DataPack {
    //##0295QN=20190312154807595;ST=22;CN=2000;PW=555555;MN=ZYJCY20190122002;Flag=5;CP=&&DataTime=20190312154807;Operation=1;s10001=0;s10002=0;s10003=23.2;s10004=24.2;s10005=45.7;s10006=24.6;s10007=50.3;s10008=12.0;s10009=0;s10010=0;s10011=0;s10012=0;s10013=1;s10014=1;s10015=1;s10016=1;s10017=0;s10018=0;&&32aa
    //##0163QN=20190103164400049;ST=39;CN=2011;PW=123456;MN=ZYJCY2019012200000000001;Flag=1;CP=&&DataTime=20190103043000;a34011-Rtd=0.1783,a34011-Ori=178.5465,a34011-Flag=N;&&ea38
    protected Boolean verifyStatus;
    protected Integer packageLength;
    protected String qnId;
    protected String stId;
    protected String cnId;
    protected String pwId;
    protected String mnId;
    protected String flag;
    protected Map body;
    @JSONField(serialize = false)
    protected Channel channel;

    @Override
    public String toString() {
        return "DataPack:\n" +
                "verifyStatus='" + verifyStatus + "'\n" +
                "packageLength='" + packageLength + "'\n" +
                "qnId='" + qnId + "'\n" +
                "stId='" + stId + "'\n" +
                "cnId='" + cnId + "'\n" +
                "pwId='" + pwId + "'\n" +
                "mnId='" + mnId + "'\n" ;
    }
}
