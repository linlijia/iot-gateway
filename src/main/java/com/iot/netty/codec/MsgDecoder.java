package com.iot.netty.codec;

import com.iot.config.IOTConfig;
import com.iot.util.bit.BitOperator;
import com.iot.bo.DataPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

@Component
public class MsgDecoder {
    private static final Logger log = LoggerFactory.getLogger(MsgDecoder.class);

    private BitOperator bitOperator;
    public MsgDecoder() {
        this.bitOperator = new BitOperator();
    }

    /**
     * 解析包信息
     * @param data
     * @return
     */
    public DataPack bytes2PackageData(byte[] data) throws Exception {
        DataPack dataPack = new DataPack();

        // 起始符
        String start = this.parseStringFromBytes(data, 0, 2);
        int packageLength = Integer.parseInt(this.parseStringFromBytes(data, 2, 4));
        System.out.println("infoLength:" + (data.length-10) + ",packageLength:" + packageLength);
        if (data.length-12 != packageLength) {
            dataPack.setVerifyStatus(false);
            return dataPack;
        }
        dataPack.setVerifyStatus(true);
        dataPack.setPackageLength(packageLength);

        String packageInfo = this.parseStringFromBytes(data, 6, data.length-10);

        String cnId = null;
        String qnId = null;
        String stId = null;
        String pwId = null;
        String mnId = null;
        String flag = null;

        Map<String, Object> bodyMap = new HashMap<String, Object>();
        String[] strs = packageInfo.split(";|&&|,");
        for(int i=0,len=strs.length;i<len;i++){
            String[] strss = strs[i].split("=");
            if (strss.length > 1) {
                if (strss[0].toLowerCase().equals("cn")) {
                    cnId = strss[1];
                    bodyMap.put(strss[0].toLowerCase(), strss[1]);
                } else if (strss[0].toLowerCase().equals("qn")) {
                    qnId = strss[1];
                    bodyMap.put(strss[0].toLowerCase(), strss[1]);
                } else if (strss[0].toLowerCase().equals("st")) {
                    stId = strss[1];
                    bodyMap.put(strss[0].toLowerCase(), strss[1]);
                } else if (strss[0].toLowerCase().equals("pw")) {
                    pwId = strss[1];
                    bodyMap.put(strss[0].toLowerCase(), strss[1]);
                } else if (strss[0].toLowerCase().equals("mn")) {
                    mnId = strss[1];
                    bodyMap.put(strss[0].toLowerCase(), strss[1]);
                } else if (strss[0].toLowerCase().equals("flag")) {
                    flag = strss[1];
                    bodyMap.put(strss[0].toLowerCase(), strss[1]);
                } else if (strss[0].toLowerCase().equals("datatime")) {
                    bodyMap.put("dataTime", this.timeParse(strss[1]));
                } else {
                    bodyMap.put(toLowerCaseFirstOne(strss[0].replaceAll("-", "")), strss[1]);
                }
            }
        }

        dataPack.setCnId(cnId);
        dataPack.setQnId(qnId);;
        dataPack.setStId(stId);
        dataPack.setPwId(pwId);
        dataPack.setMnId(mnId);
        dataPack.setFlag(flag);
        dataPack.setBody(bodyMap);

        log.info(bodyMap.toString());

        return dataPack;
    }

    protected String parseStringFromBytes(byte[] data, int startIndex, int lenth) {
        return this.parseStringFromBytes(data, startIndex, lenth, null);
    }

    private String parseStringFromBytes(byte[] data, int startIndex, int lenth, String defaultVal) {
        try {
            byte[] tmp = new byte[lenth];
            System.arraycopy(data, startIndex, tmp, 0, lenth);
            return new String(tmp, IOTConfig.string_charset);
        } catch (Exception e) {
            log.error("解析字符串出错:{}", e.getMessage());
            e.printStackTrace();
            return defaultVal;
        }
    }

    private int parseIntFromBytes(byte[] data, int startIndex, int length) {
        return this.parseIntFromBytes(data, startIndex, length, 0);
    }

    private int parseIntFromBytes(byte[] data, int startIndex, int length, int defaultVal) {
        try {
            // 字节数大于4,从起始索引开始向后处理4个字节,其余超出部分丢弃
            final int len = length > 4 ? 4 : length;
            byte[] tmp = new byte[len];
            System.arraycopy(data, startIndex, tmp, 0, len);
            return bitOperator.byteToInteger(tmp);
        } catch (Exception e) {
            log.error("解析整数出错:{}", e.getMessage());
            e.printStackTrace();
            return defaultVal;
        }
    }

    private String timeParse(String dateTime) {
        DateFormat format1 = new SimpleDateFormat("yyyyMMddHHmmss");
        DateFormat format2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            return format2.format(format1.parse(dateTime).getTime());
        } catch (ParseException e) {
            e.printStackTrace();
            return dateTime;
        }
    }

    private static String toLowerCaseFirstOne(String s){
        if(Character.isLowerCase(s.charAt(0)))
            return s;
        else
            return (new StringBuilder()).append(Character.toLowerCase(s.charAt(0))).append(s.substring(1)).toString();
    }
}
