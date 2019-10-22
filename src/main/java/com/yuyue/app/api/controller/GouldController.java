package com.yuyue.app.api.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.google.common.collect.Maps;
import com.yuyue.app.enums.ReturnResult;
import com.yuyue.app.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 高德地图API
 */

@Slf4j
@RestController
@RequestMapping(value="/gould", produces = "application/json; charset=UTF-8")
public class GouldController extends BaseController{

    @Autowired
    private SmsUtil smsUtil;
    @Autowired
    private RedisUtil redisUtil;

    private static final String signName = "娱悦APP";
    private static final String templateCode = "SMS_172100731";

    /**
     * 发送消息验证码，通用接口
     */
    @RequestMapping("/sendSms")
    @ResponseBody
    public JSONObject sendSms(HttpServletRequest request, HttpServletResponse response){
        log.info("发送消息验证码，通用接口-------------->>/send/sendSms");
        Map<String, String> map = getParameterMap(request, response);
        HashMap<String,String> hashMap = Maps.newHashMap();
        ReturnResult result =new ReturnResult();
        String lcode = RandomSaltUtil.randomNumber(6);
        try {
            map.put("template_code", templateCode);
            map.put("sign_name", signName);
            hashMap.put("code",lcode);
            map.put("param", JSON.toJSON(hashMap).toString());
            log.info("验证码：=========="+lcode);
            SendSmsResponse sendSmsResponse = smsUtil.sendSms(
                    map.get("mobile"), map.get("template_code") , map.get("sign_name")  , map.get("param") );

            if("OK".equals(sendSmsResponse.getCode()) ){
//            if("OK".equals("OK") ){
                redisUtil.setString(map.get("mobile"),lcode);
                redisUtil.expire(map.get("mobile"),5L*60L);
                result.setMessage("短信发送成功!");
                result.setStatus(Boolean.TRUE);
//                result.setResult(lcode);
            } else {
                result.setMessage("短信发送失败！");
                log.info("短信发送失败！");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.setMessage("短信发送失败！");
            log.info("短信发送失败！");
        }
        return ResultJSONUtils.getJSONObjectBean(result);
    }
}