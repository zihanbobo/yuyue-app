package com.yuyue.app.utils;

import com.alibaba.fastjson.JSONObject;
import com.yuyue.app.api.domain.AppUser;
import com.yuyue.app.enums.ReturnResult;
import it.sauronsoftware.jave.Encoder;
import it.sauronsoftware.jave.EncoderException;
import it.sauronsoftware.jave.MultimediaInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.text.DecimalFormat;

public class ResultJSONUtils {
    private static Logger log = LoggerFactory.getLogger(ResultJSONUtils.class);

    public static JSONObject getJSONObjectBean(ReturnResult returnResult){
        return JSONObject.parseObject(JSONObject.toJSON(returnResult).toString());
    }

    /**
     * 获取hash值分表，物理分表，视频的
     * @param tableName
     * @param tableId
     * @return
     */
    public static String getHashValue(String tableName, String tableId){
        StringBuilder name = new StringBuilder(tableName);
        int temp = Math.abs(tableId.hashCode()) % 2 == 0 ? 0 : 1; //几张表，就是几
        String append = name.append(temp).toString();
        return append;
    }

    /**
     * 获取hash值分表，物理分表，评论的
     * @param tableName
     * @param tableId
     * @return
     */
    public static String getHash(String tableName, String tableId){
        StringBuilder name = new StringBuilder(tableName);
        int temp = Math.abs(tableId.hashCode()) % 5; //几张表，就是几
        String append = name.append(temp).toString();
        return append;
    }

    /**
     * 格式化 文件大小
     * @param fileSize
     * @return
     */
    public static String getSize(Double fileSize){
//        System.out.println("获取文件大小======="+fileSize);
        //数据转化   B-->KB-->MB
        String size = "";
        DecimalFormat df = new DecimalFormat("#.00");
        if(1024 > fileSize){
            size = fileSize+"B";
        }else if (1024 < fileSize && 1048576 > fileSize){
            size = df.format(fileSize/1024)+"KB";
        }else {
            size = df.format(fileSize/1024/1024)+"MB";
        }
        return size;
    }

    /**
     * 获取本地视频时长
     * @return
     */
    public static String getVideoSize(String pathUrl){
//        System.out.println("获取文件路径======="+pathUrl);
        //获取文件时长
        String videoSize = "";
        Encoder encoder=new Encoder();
        try {
            File localFile=new File(pathUrl);
            long duration = encoder.getInfo(localFile).getDuration();
//            System.out.println("视频时长"+duration);
            //时分秒
            long secondTotal=duration/1000;
            if (secondTotal < 60){
                videoSize = "0:"+secondTotal;
            }else if(secondTotal > 60 && secondTotal < 3600){
                int minute = (int)secondTotal / 60;
                int second=(int)secondTotal  %  60;
                videoSize = minute+":"+second;
            }else {
                int hour=(int)secondTotal / 3600;
                int minute = (int)secondTotal / 60;
                int second=(int)secondTotal  %  60;
                videoSize = hour+":"+minute+":"+second;
            }
        } catch (EncoderException e) {
            e.printStackTrace();
            log.info("视频格式转化失败！");
        }
        return videoSize;
    }

    /**
     * 获取URL视频时长
     * @return
     */
    public static String getVideoUrl(String pathUrl){
//        System.out.println("获取文件路径======="+pathUrl);
        //获取文件时长
        String videoSize = "";
        Encoder encoder=new Encoder();
        try {
            //获取网络文件，暂存为临时文件
            File tmpFile = File.createTempFile("temp", ".tmp");//创建临时文件
            toBDFile(pathUrl, tmpFile.getCanonicalPath());

            MultimediaInfo m = new Encoder().getInfo(tmpFile);
            //时分秒
            long secondTotal=m.getDuration() / 1000;
            if (secondTotal < 60){
                videoSize = "0:"+secondTotal;
            }else if(secondTotal > 60 && secondTotal < 3600){
                int minute = (int)secondTotal / 60;
                int second=(int)secondTotal  %  60;
                videoSize = minute+":"+second;
            }else {
                int hour=(int)secondTotal / 3600;
                int minute = (int)secondTotal / 60;
                int second=(int)secondTotal  %  60;
                videoSize = hour+":"+minute+":"+second;
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.info("视频格式转化失败！");
        }
        return videoSize;
    }

    /**
     * @throws IOException
     * 网络文件转换为本地文件
     * @Title: toByteArray
     * @Description: TODO(这里用一句话描述这个方法的作用)
     * @param @param url
     * @param @return
     * @param @throws IOException    设定文件
     * @return byte[]    返回类型
     * @throws
     */
    public static void toBDFile(String urlStr, String bdUrl) throws Exception{
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        DataInputStream in = new DataInputStream(conn.getInputStream());
        byte[] data=toByteArray(in);
        in.close();
        FileOutputStream out=new FileOutputStream(bdUrl);
        out.write(data);
        out.close();
    }

    /**
     * 网络文件转换为byte二进制
     * @Title: toByteArray
     * @Description: TODO(这里用一句话描述这个方法的作用)
     * @param @param url
     * @param @return
     * @param @throws IOException    设定文件
     * @return byte[]    返回类型
     * @throws
     */
    public static byte[] toByteArray(InputStream in) throws IOException {

        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[1024*4];
        int n=0;
        while ( (n=in.read(buffer)) !=-1) {
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }

    /**
     * 加减钱
     * @param userMoney 用户的钱
     * @param money    加减的钱
     * @return
     */
    public synchronized static BigDecimal updateUserMoney(BigDecimal userMoney,BigDecimal money,String symbol){
       if (userMoney.compareTo(BigDecimal.ZERO) == -1){
            throw MyExceptionUtils.mxe("用户的钱不能为负！");
        } else if (money.compareTo(BigDecimal.ZERO) == -1){
            throw MyExceptionUtils.mxe("加减的钱不能为负！");
        }
        if("+".equals(symbol)){
            return userMoney.add(money);
        }
        return userMoney.subtract(money);
    }

    public synchronized static BigDecimal updateMIncome(AppUser user,BigDecimal money,String symbol){
        if(StringUtils.isNull(user)){
            throw MyExceptionUtils.mxe("加钱失败！用户为空！");
        } else if (money == null){
            throw MyExceptionUtils.mxe("钱为空！");
        } else if (money.compareTo(BigDecimal.ZERO) == -1){
            throw MyExceptionUtils.mxe("钱不能为负！");
        }
        if("+".equals(symbol)){
            return user.getMIncome().add(money);
        }
        return user.getMIncome().subtract(money);
    }
}
