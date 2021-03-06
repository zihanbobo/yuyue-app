package com.yuyue.app.api.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.AlipayFundTransToaccountTransferResponse;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.auth0.jwt.JWT;
import com.google.common.collect.Maps;
import com.yuyue.app.annotation.CurrentUser;
import com.yuyue.app.annotation.LoginRequired;
import com.yuyue.app.api.domain.*;
import com.yuyue.app.api.service.LoginService;
import com.yuyue.app.api.service.MallShopService;
import com.yuyue.app.api.service.MyService;
import com.yuyue.app.api.service.PayService;
import com.yuyue.app.enums.ReturnResult;
import com.yuyue.app.enums.Variables;
import com.yuyue.app.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@RestController
@RequestMapping(value = "/pay", produces = "application/json; charset=UTF-8")
public class PayController extends BaseController{
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private PayService payService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private MyService myService;
    @Autowired
    private MallShopService mallShopService;
    @Autowired
    private RedisTemplate redisTemplate;

    // 构造签名的map
    private SortedMap<Object, Object> parameters = new TreeMap<>();
    //苹果内购
    private static final Map<String, Object> iosMap = new HashMap<>();
    static {
        //1 3 6 12 30 50
        iosMap.put("1",new BigDecimal(0.7));
        iosMap.put("3",new BigDecimal(2.1));
        iosMap.put("6",new BigDecimal(4.2));
        iosMap.put("12",new BigDecimal(8.4));
        iosMap.put("30",new BigDecimal(21));
        iosMap.put("50",new BigDecimal(35));
    }

    @ResponseBody
    @RequestMapping("/payYuYue")
    @LoginRequired
    public JSONObject payYuYue(Order order, @CurrentUser AppUser user) throws Exception {
        ReturnResult returnResult = new ReturnResult();
        log.info("-------创建充值订单-----------");
        if (StringUtils.isEmpty(order.getTradeType())) {
            returnResult.setMessage("充值类型不能为空！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        if (!order.getTradeType().contains("XF")){
            order.setOrderNo("YY"+ order.getTradeType() + RandomSaltUtil.randomNumber(14));
            order.setStatus("10A");
            order.setStatusCode("100001");
            order.setMobile(user.getPhone());
            order.setMerchantId(user.getId());
//          order.setTradeType("CZWX");
//          order.setMoney("100");
            createOrder(order);
            if (StringUtils.isEmpty(order.getId())) {
                returnResult.setMessage("创建订单失败！缺少参数！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
            if (order.getTradeType().contains("WX")) {
                return payWX(order);
            } else if (order.getTradeType().contains("ZFB")) {
                return payZFB(order);
            }
        } else if (order.getTradeType().contains("XF")) {
            if (StringUtils.isEmpty(order.getVideoId())) {
                returnResult.setMessage("视频ID不可以为空！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            } else if (StringUtils.isEmpty(order.getSourceId())) {
                returnResult.setMessage("艺人ID不可以为空！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
            AppUser appUser = loginService.getAppUserMsg("","",order.getSourceId());
            if(StringUtils.isNull(appUser)){
                returnResult.setMessage("您想送礼的用户，不存在！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
            ChangeMoney xfMoney = new ChangeMoney();
            xfMoney.setChangeNo("YY"+order.getTradeType() + RandomSaltUtil.randomNumber(14));
            xfMoney.setStatus("10A");
            xfMoney.setMobile(user.getPhone());
            xfMoney.setMerchantId(user.getId());
            xfMoney.setSourceId(appUser.getId());
            xfMoney.setNote("用户消费");
            xfMoney.setTradeType(order.getTradeType());
            xfMoney.setMoney(order.getMoney());
            xfMoney.setVideoId(order.getVideoId());
            createShouMoney(xfMoney);

            order.setId(xfMoney.getId());
            if (order.getTradeType().contains("WX")) {
                return payWX(order);
            } else if (order.getTradeType().contains("ZFB")) {
                return payZFB(order);
            }
        }
        returnResult.setMessage("充值类型选择错误！！");
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    public JSONObject payWX(Order order) throws Exception {
        ReturnResult returnResult = new ReturnResult();
        log.info("-------weixinAPP支付统一下单-----------");
        log.info("订单详情============" + order.toString());
        try {
            Map map = new HashMap();
            log.info("金额==========>>>"+order.getMoney());
            String moneyD = order.getMoney().setScale(2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100))
                    .setScale(0,BigDecimal.ROUND_HALF_UP).toString();
            log.info("金额==========>>>"+moneyD);
            map.put("appid", Variables.wxAppId);
            map.put("mch_id", Variables.wxMchID);
            map.put("nonce_str", RandomSaltUtil.generetRandomSaltCode(32));
            if((order.getTradeType()).contains("GG")){
                map.put("body", "yuyue-广告费用");
            }else if((order.getTradeType()).contains("XF")){
                map.put("body", "yuyue-视频打赏");
            } else {
                map.put("body", "yuyue-"+Variables.subject);
            }
            map.put("out_trade_no", order.getId());
            map.put("total_fee", moneyD);
            map.put("spbill_create_ip", Variables.ip);
            map.put("trade_type", "APP");
            map.put("notify_url", Variables.wxNotifyUrl);
            String sign = MD5Utils.signDatashwx(map, Variables.wxKEY);
            map.put("sign", sign);
            StringBuffer sb = new StringBuffer();
            sb.append("<xml>");
            XMLUtils.mapToXMLTest2(map, sb);
            sb.append("</xml>");
            log.info((new StringBuilder()).append("上送的数据为+++++++").append(sb.toString()).toString());
            String res = XMLUtils.doPost("https://api.mch.weixin.qq.com/pay/unifiedorder", sb.toString(), Variables.CHARSET, "application/json");
            log.info("返回的数据为--------------------------+++++++" + res);
            Map ValidCard = XMLUtils.xmlString2Map(res);
            Map maps = new HashMap();
            String timestamp = String.valueOf((new Date()).getTime() / 1000L);
            maps.put("appid", ValidCard.get("appid").toString());
            maps.put("partnerid", Variables.wxMchID);
            maps.put("prepayid", ValidCard.get("prepay_id"));
            maps.put("package", "Sign=WXPay");
            maps.put("noncestr", ValidCard.get("nonce_str"));
            maps.put("timestamp", timestamp);
            //maps.put("signType", "MD5");
            String signs = MD5Utils.signDatashwx(maps, Variables.wxKEY);
            maps.put("sign", signs);
            //        return JSONObject.toJSONString(maps);
            maps.put("orderId",order.getId());
            returnResult.setMessage("返回成功！");
            returnResult.setStatus(Boolean.TRUE);
            returnResult.setResult(JSONObject.toJSON(maps));
        } catch (Exception e) {
            e.printStackTrace();
            log.info("支付失败！参数不对！");
            returnResult.setMessage("支付失败！参数不对！");
            payService.updateStatus(order.getId(), "10C");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * @throws Exception
     * @Title:wxpayNotify
     * @Description:微信回调
     * @date:2018年7月18日 下午2:32:49
     */
    @ResponseBody
    @RequestMapping(value = "/wxpayNotify")
    public synchronized void wxpay(HttpServletRequest request) throws Exception {
        log.info((new StringBuilder()).append("回调的内容为+++++++++++++++++++++++++++++++++").append(request).toString());
        BufferedReader br = new BufferedReader(new InputStreamReader(request.getInputStream(), Variables.CHARSET));
        StringBuffer buffer = new StringBuffer();
        for (String line = " "; (line = br.readLine()) != null; )
            buffer.append(line);

        log.info((new StringBuilder()).append("内容++++++++++++").append(buffer.toString()).toString());
        Map object = XMLUtils.xmlString2Map(buffer.toString());
        log.info((new StringBuilder()).append("返回的数据是+++++++").append(object).toString());
        String returnCode = object.get("return_code").toString();
        String orderId = object.get("out_trade_no").toString();
        log.info((new StringBuilder()).append("\u56DE\u8C03\uFF1A").append(orderId).toString());
        if (StringUtils.isNotEmpty(orderId)) {
            //卖出商品
            Order orderNo = payService.getOrderId(orderId);
            BigDecimal subtract = BigDecimal.ZERO;
            if (StringUtils.isNotNull(orderNo)) {
                if (!"10B".equals(orderNo.getStatus()) && returnCode.equals("SUCCESS")) {
                    orderNo.setResponseCode(object.get("transaction_id").toString());
                    orderNo.setResponseMessage(object.get("result_code").toString());
                    orderNo.setStatus("10B");
                    payService.updateOrderStatus(orderNo.getResponseCode(), orderNo.getResponseMessage(), orderNo.getStatus(), orderNo.getOrderNo());
//                    AppUser appUser = loginService.getAppUserMsg("","",orderNo.getMerchantId());
//                    if(orderNo.getTradeType().contains("CZ") || orderNo.getTradeType().contains("SM")){
//                        BigDecimal add = ResultJSONUtils.updateTotalMoney(appUser,orderNo.getMoney(),"+");
//                        payService.updateTotal(appUser.getId(), add);
//                    }
                    if (orderNo.getTradeType().contains("SC") || orderNo.getTradeType().contains("WAP")
                            || orderNo.getTradeType().contains("SM")){
                        log.info("----------------给商户加钱----------------");
                        mallShopService.mallPaySuccess(orderId);
                    }else if(orderNo.getTradeType().contains("GG")){
                        log.info("----------------商户申请，支付成功----------------");
                        mallShopService.getMyMallShopByOrderId(orderId);
                    }
                } else if (!"10B".equals(orderNo.getStatus()) && !"SUCCESS".equals(returnCode)) {
                    orderNo.setResponseCode(returnCode);
                    orderNo.setResponseMessage(object.get("result_code").toString());
                    orderNo.setStatus("10C");
                    payService.updateOrderStatus(orderNo.getResponseCode(), orderNo.getResponseMessage(), orderNo.getStatus(), orderNo.getOrderNo());
                }
            }
            //送礼
            ChangeMoney changeMoney = myService.getChangeMoney(orderId,"");
            if (StringUtils.isNotNull(changeMoney) && changeMoney.getTradeType().contains("XF")) {//送礼
                if (!"10B".equals(changeMoney.getStatus()) && returnCode.equals("SUCCESS")) {
                    changeMoney.setResponseCode(returnCode);
                    changeMoney.setResponseMessage(object.get("result_code").toString());
                    changeMoney.setStatus("10B");
                    payService.updateChangeMoneyStatus(subtract,changeMoney.getResponseCode(), changeMoney.getResponseMessage(), changeMoney.getStatus(), changeMoney.getId());

                    AppUser appUser = loginService.getAppUserMsg("","",changeMoney.getSourceId());
                    BigDecimal bigDecimal = changeMoney.getMoney().multiply(new BigDecimal(0.6)).setScale(2, BigDecimal.ROUND_HALF_UP);
                    subtract = ResultJSONUtils.updateUserMoney(appUser.getIncome(), bigDecimal, "+");
                    payService.updateOutIncome(appUser.getId(),subtract);

                    ChangeMoney syMoney = new ChangeMoney();
                    syMoney.setChangeNo("YYSY" + RandomSaltUtil.randomNumber(14));
                    syMoney.setStatus("10B");
                    syMoney.setMobile(appUser.getPhone());
                    syMoney.setMerchantId(appUser.getId());
                    syMoney.setSourceId(changeMoney.getMerchantId());
                    syMoney.setMoney(bigDecimal);
                    syMoney.setNote("用户收益");
                    syMoney.setTradeType("SY");
                    syMoney.setHistoryMoney(subtract);
                    createShouMoney(syMoney);

                    syMoney.setResponseCode(returnCode);
                    syMoney.setResponseMessage(object.get("result_code").toString());
                    payService.updateChangeMoneyStatus(subtract,syMoney.getResponseCode(), syMoney.getResponseMessage(), syMoney.getStatus(), syMoney.getId());
                } else if (!"10B".equals(changeMoney.getStatus()) && !"SUCCESS".equals(returnCode)) {
                    changeMoney.setResponseCode(returnCode);
                    changeMoney.setResponseMessage(object.get("result_code").toString());
                    changeMoney.setStatus("10C");
                    payService.updateChangeMoneyStatus(subtract,changeMoney.getResponseCode(), changeMoney.getResponseMessage(), changeMoney.getStatus(), changeMoney.getId());
                }
            }
        }
    }

    /**
     * 支付宝APP支付统一下单接口
     *
     * @return
     */
    public JSONObject payZFB(Order order) throws Exception {
        ReturnResult returnResult = new ReturnResult();
        log.info("======支付宝APP支付统一下单接口==============");
        try {
            // 实例化具体API对应的request类,类名称和接口名称对应,当前调用接口名称：alipay.trade.app.pay
            AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
            // SDK已经封装掉了公共参数，这里只需要传入业务参数。以下方法为sdk的model入参方式(model和biz_content同时存在的情况下取biz_content)。
            AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
            if((order.getTradeType()).contains("GG")){
                model.setBody("yuyue-广告费用");
                model.setSubject("yuyue-广告费用");
            }else if((order.getTradeType()).contains("XF")){
                model.setBody("yuyue-视频打赏");
                model.setSubject("yuyue-视频打赏");
            } else {
                model.setBody("yuyue-"+Variables.subject);
                model.setSubject("yuyue-"+Variables.subject);
            }
            model.setOutTradeNo(order.getId());
            model.setTimeoutExpress("30m");

            // 将分制金额换成元制金额保留两位小数
            String moneyD = order.getMoney().setScale(2, BigDecimal.ROUND_HALF_UP).toString();
            model.setTotalAmount(moneyD);
//            model.setProductCode("QUICK_MSECURITY_PAY");// 固定值
            model.setProductCode("FAST_INSTANT_TRADE_PAY");// 固定值
            request.setBizModel(model);
            request.setNotifyUrl(Variables.AliPayNotifyUrl);// 商户外网可以访问的异步地址
            try {
                // 这里和普通的接口调用不同，使用的是sdkExecute
                AlipayTradeAppPayResponse response = Variables.alipayClient.sdkExecute(request);
                log.info("response: " + response.getBody());// 就是orderString
                // 可以直接给客户端请求，无需再做处理。
                returnResult.setMessage("返回成功！");
                returnResult.setStatus(Boolean.TRUE);
                Map map = new HashMap();
                map.put("response",response.getBody());
                map.put("orderId",order.getId());
//                String[] split = response.getBody().split("&");
//                for (int i = 0; i < split.length; i++) {
//                    map.put(split[i].split("=")[0],split[i].split("=")[1]);
//                }
                returnResult.setResult(map);
            } catch (AlipayApiException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.info("支付失败！参数不对！");
            returnResult.setMessage("支付失败！参数不对！");
            payService.updateStatus(order.getId(), "10C");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 支付宝回调
     *
     * @param request
     * @param response
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping(value = "/alipayNotify")
    public synchronized void alipayNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        getParameterMap(request, response);
        log.info("支付宝平台回调开始+++++++++++++++++++++++++++++++++");
        // 获取支付宝POST过来反馈信息
        Map<String, String> params = new HashMap<>();
        Map requestParams = request.getParameterMap();
        for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext(); ) {
            String name = (String) iter.next();
            String[] values = (String[]) requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            // 乱码解决，这段代码在出现乱码时使用。
            // valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
            params.put(name, valueStr);
        }
        log.info("params: " + params);
        // 切记alipaypublickey是支付宝的公钥，请去open.alipay.com对应应用下查看。
        // boolean AlipaySignature.rsaCheckV1(Map<String, String> params, String
        // publicKey, String charset, String sign_type)
        String orderId = params.get("out_trade_no");
        boolean flag = AlipaySignature.rsaCheckV1(params, Variables.AliPayPublicKey, Variables.CHARSET, "RSA2");
        if (flag) {
            log.info("支付宝验签成功+++++++++++++++++++++++++++++++++");
            //卖出商品
            Order orderNo = payService.getOrderId(orderId);
            BigDecimal subtract = BigDecimal.ZERO;
            if (StringUtils.isNotNull(orderNo)) {
                // 有可能出现多次回调，只有在该状态下的回调才是支付成功下的回调
                if (!"10B".equals(orderNo.getStatus()) && (params.get("trade_status").equals("TRADE_SUCCESS")
                        || params.get("trade_status").equals("TRADE_FINISHED"))) {
                    log.info("加钱===================");
                    String trxNo = params.get("trade_status");
                    //加钱
                    orderNo.setResponseCode(params.get("trade_no"));
                    orderNo.setResponseMessage(trxNo);
                    orderNo.setStatus("10B");
                    payService.updateOrderStatus(orderNo.getResponseCode(), orderNo.getResponseMessage(), orderNo.getStatus(), orderNo.getOrderNo());

                    if (orderNo.getTradeType().contains("SC")|| orderNo.getTradeType().contains("WAP")
                            || orderNo.getTradeType().contains("SM")){
                        log.info("----------------给商户加钱----------------");
                        mallShopService.mallPaySuccess(orderId);
                    }else if(orderNo.getTradeType().contains("GG")){
                        log.info("----------------商户申请，支付成功----------------");
                        mallShopService.getMyMallShopByOrderId(orderId);
                    }
//                    AppUser appUser = loginService.getAppUserMsg("","",orderNo.getMerchantId());
//                    if(orderNo.getTradeType().contains("CZ") || orderNo.getTradeType().contains("SM")) {
//                        BigDecimal add = ResultJSONUtils.updateTotalMoney(appUser, orderNo.getMoney(), "+");
//                        payService.updateTotal(appUser.getId(), add);
//                    }
                } else if (!"10B".equals(orderNo.getStatus()) && (!params.get("trade_status").equals("TRADE_SUCCESS") && !params.get("trade_status").equals("TRADE_FINISHED"))){
                    log.info("不加钱===================");
                    String trxNo = params.get("trade_status");
                    //加钱
                    orderNo.setResponseCode(trxNo);
                    orderNo.setResponseMessage(trxNo);
                    orderNo.setStatus("10C");
                    payService.updateOrderStatus(orderNo.getResponseCode(), orderNo.getResponseMessage(), orderNo.getStatus(), orderNo.getOrderNo());
                }
            }
            //送礼
            ChangeMoney changeMoney = myService.getChangeMoney(orderId,"");
            if (StringUtils.isNotNull(changeMoney) && changeMoney.getTradeType().contains("XF")) {//送礼
                if (!"10B".equals(changeMoney.getStatus()) && (params.get("trade_status").equals("TRADE_SUCCESS") || params.get("trade_status").equals("TRADE_FINISHED"))) {
                    changeMoney.setResponseCode(params.get("trade_status"));
                    changeMoney.setResponseMessage(params.get("trade_status"));
                    changeMoney.setStatus("10B");
                    payService.updateChangeMoneyStatus(subtract,changeMoney.getResponseCode(), changeMoney.getResponseMessage(), changeMoney.getStatus(), changeMoney.getId());

                    AppUser appUser = loginService.getAppUserMsg("","",changeMoney.getSourceId());
                    BigDecimal bigDecimal = changeMoney.getMoney().multiply(new BigDecimal(0.6)).setScale(2, BigDecimal.ROUND_HALF_UP);
                    subtract = ResultJSONUtils.updateUserMoney(appUser.getIncome(), bigDecimal, "+");
                    payService.updateOutIncome(appUser.getId(),subtract);

                    ChangeMoney syMoney = new ChangeMoney();
                    syMoney.setChangeNo("YYSY" + RandomSaltUtil.randomNumber(14));
                    syMoney.setStatus("10B");
                    syMoney.setMobile(appUser.getPhone());
                    syMoney.setMerchantId(appUser.getId());
                    syMoney.setSourceId(changeMoney.getMerchantId());
                    syMoney.setMoney(bigDecimal);
                    syMoney.setNote("用户收益");
                    syMoney.setTradeType("SY");
                    syMoney.setHistoryMoney(subtract);
                    createShouMoney(syMoney);

                    syMoney.setResponseCode(params.get("trade_status"));
                    syMoney.setResponseMessage(params.get("trade_status"));
                    payService.updateChangeMoneyStatus(subtract,syMoney.getResponseCode(), syMoney.getResponseMessage(), syMoney.getStatus(), syMoney.getId());
                } else if (!"10B".equals(changeMoney.getStatus()) && (!params.get("trade_status").equals("TRADE_SUCCESS") && !params.get("trade_status").equals("TRADE_FINISHED"))){
                    changeMoney.setResponseCode(params.get("trade_status"));
                    changeMoney.setResponseMessage(params.get("trade_status"));
                    changeMoney.setStatus("10C");
                    payService.updateChangeMoneyStatus(subtract,changeMoney.getResponseCode(), changeMoney.getResponseMessage(), changeMoney.getStatus(), changeMoney.getId());
                }
            }
            log.info("支付宝平台回调结束+++++++++++++++++++++++++++++++++");
        }
    }

    /**
     * @param TransactionID        ：交易单号 需要客户端传过来的参数1
     * @param Payload：需要客户端传过来的参数2
     * @throws Exception 苹果内购支付
     * @throws
     * @Title: doIosRequest
     * @Description:Ios客户端内购支付
     */
    @ResponseBody
    @RequestMapping(value = "/doIosRequest")
    @LoginRequired
    public synchronized JSONObject doIosRequest(String videoId,String sourceId,String TransactionID, String Payload, @CurrentUser AppUser user) throws Exception {
        ReturnResult returnResult = new ReturnResult();
        if (StringUtils.isEmpty(videoId)) {
            returnResult.setMessage("视频ID不可以为空！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (StringUtils.isEmpty(sourceId)) {
            returnResult.setMessage("艺人ID不可以为空！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (StringUtils.isEmpty(TransactionID)) {
            returnResult.setMessage("TransactionID不可以为空！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (StringUtils.isEmpty(Payload)) {
            returnResult.setMessage("Payload不可以为空！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        AppUser appUser = loginService.getAppUserMsg("","",sourceId);
        if(StringUtils.isNull(appUser)){
            returnResult.setMessage("您想送礼的用户，不存在！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }

        System.out.println("客户端传过来的值1：" + TransactionID + "客户端传过来的值2：" + Payload);
        String verifyResult = IosVerifyUtils.buyAppVerify(Payload, 1); //1.先线上测试 发送平台验证
        if (verifyResult == null) { // 苹果服务器没有返回验证结果
            returnResult.setMessage("无订单信息!");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else { // 苹果验证有返回结果
            System.out.println("线上，苹果平台返回JSON:" + verifyResult);
            JSONObject job = JSONObject.parseObject(verifyResult);
            String states = job.getString("status");

            if ("21007".equals(states)) {    //是沙盒环境，应沙盒测试，否则执行下面
                verifyResult = IosVerifyUtils.buyAppVerify(Payload, 0);    //2.再沙盒测试 发送平台验证
                System.out.println("沙盒环境，苹果平台返回JSON:" + verifyResult);
                job = JSONObject.parseObject(verifyResult);
                states = job.getString("status");
            }

            System.out.println("苹果平台返回值：job" + job);
            BigDecimal subtract = BigDecimal.ZERO;
            if (states.equals("0")) { // 前端所提供的收据是有效的 验证成功
                String r_receipt = job.getString("receipt");
                JSONObject returnJson = JSONObject.parseObject(r_receipt);
                String in_app = returnJson.getString("in_app");
                JSONObject in_appJson = JSONObject.parseObject(in_app.substring(1, in_app.length() - 1));

                String product_id = in_appJson.getString("product_id");
                String transaction_id = in_appJson.getString("transaction_id"); // 订单号

                String[] moneys = product_id.split("\\.");
                ChangeMoney xfMoney = new ChangeMoney();
                xfMoney.setChangeNo("YYXF" + RandomSaltUtil.randomNumber(14));
                xfMoney.setStatus("10A");
                xfMoney.setMobile(user.getPhone());
                xfMoney.setMerchantId(user.getId());
                xfMoney.setSourceId(appUser.getId());
                xfMoney.setNote(moneys[3]);
                xfMoney.setTradeType("XFIOS");
                xfMoney.setMoney(new BigDecimal(iosMap.get(moneys[3]).toString()));
                xfMoney.setVideoId(videoId);
                createShouMoney(xfMoney);

                //如果单号一致 则保存到数据库
                if (TransactionID.equals(transaction_id)) {
                    //送礼
                    BigDecimal bigDecimal = (xfMoney.getMoney().multiply(new BigDecimal(0.6))).setScale(2, BigDecimal.ROUND_HALF_UP);
                    subtract = ResultJSONUtils.updateUserMoney(appUser.getIncome(), bigDecimal, "+");
                    payService.updateOutIncome(appUser.getId(),subtract);
                    payService.updateChangeMoneyStatus(subtract,"SUCCESS", "苹果内购成功", "10B", xfMoney.getId());

                    ChangeMoney syMoney = new ChangeMoney();
                    syMoney.setChangeNo("YYSY" + RandomSaltUtil.randomNumber(14));
                    syMoney.setStatus("10B");
                    syMoney.setMobile(appUser.getPhone());
                    syMoney.setMerchantId(appUser.getId());
                    syMoney.setSourceId(xfMoney.getMerchantId());
                    syMoney.setMoney(bigDecimal);
                    syMoney.setNote("用户收益");
                    syMoney.setTradeType("SY");
                    syMoney.setHistoryMoney(subtract);
                    createShouMoney(syMoney);

                    payService.updateChangeMoneyStatus(subtract,"SUCCESS", "苹果内购成功", "10B", syMoney.getId());
                    returnResult.setStatus(Boolean.TRUE);
                    returnResult.setMessage("充值成功！");
                    returnResult.setResult(moneys[3]);
                } else {
                    payService.updateChangeMoneyStatus(subtract,"ERROR", "receipt数据有问题", "10C", xfMoney.getId());
                }
            } else {
                returnResult.setMessage("receipt数据有问题");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
        }
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    //创建充值订单
    public void createOrder(Order order) {
        order.setId(RandomSaltUtil.generetRandomSaltCode(32));
        payService.createOrder(order);
    }

    //创建提现订单
    public void createOut(OutMoney outMoney) {
        outMoney.setId(RandomSaltUtil.generetRandomSaltCode(32));
        payService.createOut(outMoney);
    }

    //创建账户流水订单
    public void createShouMoney(ChangeMoney changeMoney) {
        changeMoney.setId(RandomSaltUtil.generetRandomSaltCode(32));
        payService.createShouMoney(changeMoney);
    }

    /**
     * 用户提现
     * @param user
     * @return
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping("/outMoney")
    @LoginRequired
    public JSONObject outMoney(String tradeType,BigDecimal money,String note,String code,String phone,@CurrentUser AppUser user) throws Exception {
        ReturnResult returnResult = new ReturnResult();
        log.info("-------提现订单-----------pay/outMoney");
        if (StringUtils.isEmpty(tradeType)) {
            returnResult.setMessage("提现类型不能为空！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (money == null || money.compareTo(BigDecimal.ZERO)==0){
            returnResult.setMessage("提现不能为空！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if ("income".equals(note) && user.getIncome().compareTo(money) == -1){
            returnResult.setMessage("提现不能高于收益！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if ("mIncome".equals(note) && user.getMIncome().compareTo(money) == -1){
            returnResult.setMessage("提现不能高于收益！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (money.compareTo(new BigDecimal(5001))==1){
            returnResult.setMessage("提现不能高于5000元！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (money.compareTo(new BigDecimal(1))==-1){
            returnResult.setMessage("提现不能低于1元！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        if (StringUtils.isEmpty(code) || code.length() != 6 || !code.equals(redisTemplate.opsForValue().get(phone).toString())) {
            returnResult.setMessage("验证码输入错误！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }

        ChangeMoney changeTime = payService.getChangeMoneyByTime(user.getId());
        if (StringUtils.isNotNull(changeTime)) {
            //上次退款时间
            Date parse = dateFormat.parse(changeTime.getCreateTime());
            long goTime = parse.getTime()/ 1000;
            log.info("上次退款时间====>>>>"+dateFormat.format(parse)+"秒数====>>>>"+goTime);
            //当前系统时间
            Date date = dateFormat.parse(dateFormat.format(new Date()));
            Long toTime = date.getTime()/ 1000;
            log.info("当前系统时间====>>>>"+dateFormat.format(date)+"秒数====>>>>"+toTime);
            if ((toTime - goTime) < 30 ) {
                returnResult.setMessage("您好！30秒内，只能退款一次！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
        }
        ChangeMoney changeMoney = new ChangeMoney();
        changeMoney.setNote(note);
        changeMoney.setMoney(money);
        changeMoney.setTradeType(tradeType);
        changeMoney.setMerchantId(user.getId());
        changeMoney.setMobile(user.getPhone());
        changeMoney.setChangeNo("YYTX" + RandomSaltUtil.randomNumber(14));
        if ("mIncome".equals(note)) {
            changeMoney.setHistoryMoney(user.getMIncome());
        } else {
            changeMoney.setHistoryMoney(user.getIncome());
        }
        if (StringUtils.isNotEmpty(user.getZfbNumber()) && StringUtils.isNotEmpty(user.getZfbRealName())) {
            changeMoney.setRealName(user.getZfbRealName());
            changeMoney.setMoneyNumber(user.getZfbNumber());
        } else {
            changeMoney.setRealName(user.getWechatName());
            changeMoney.setMoneyNumber(user.getOpendId());
        }
        if ("10B".equals(changeMoney.getStatus())) {
            returnResult.setMessage("请勿重复点击！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        changeMoney.setStatus("10B");
        createShouMoney(changeMoney);
        if (StringUtils.isEmpty(changeMoney.getId())) {
            returnResult.setMessage("创建提现订单失败！缺少参数！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }

        //提现是艺人和推荐奖励金的收益，商城收益
//        changeMoney.setNote();
//        changeMoney.setTradeType("TXZFB");
//        changeMoney.setMoney(new BigDecimal("1"));
        if ("TXZFB".equals(changeMoney.getTradeType())) {
            if (StringUtils.isEmpty(user.getZfbNumber()) && StringUtils.isEmpty(user.getZfbRealName())){
                returnResult.setCode("03");
                returnResult.setMessage("支付宝授权信息为空！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
            return outZFB(changeMoney,user);
        } else if ("TXWX".equals(changeMoney.getTradeType())) {
            if ( StringUtils.isEmpty(user.getOpendId())){
                returnResult.setCode("02");
                returnResult.setMessage("openId为空！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
            return outWX(changeMoney,user);
        }
        returnResult.setMessage("提现正在进行中！！");
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    //单笔提现到微信
    private synchronized JSONObject outWX(ChangeMoney changeMoney,AppUser user) {
        ReturnResult returnResult = new ReturnResult();
        String nonce_str = RandomSaltUtil.getRandomString(16);
        //是否校验用户姓名 NO_CHECK：不校验真实姓名 FORCE_CHECK：强校验真实姓名
        String checkName ="NO_CHECK";
        String partner_trade_no = RandomSaltUtil.generetRandomSaltCode(32);
        //描述
        log.info("/pay/outWX -----> 转账WX金额==========>>>"+changeMoney.getMoney());
        //手续费0.75%
        BigDecimal rate = changeMoney.getMoney().multiply(new BigDecimal(0.0075)).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateMoney = changeMoney.getMoney().subtract(rate).setScale(2, BigDecimal.ROUND_HALF_UP);
        String moneyD = rateMoney.multiply(new BigDecimal(100)).setScale(0,BigDecimal.ROUND_HALF_UP).toString();

        log.info("金额==========>>>"+moneyD);
        String desc = "娱悦APP提现"+changeMoney.getMoney().setScale(2, BigDecimal.ROUND_HALF_UP).toString()+"元";
        // 参数：开始生成第一次签名
        parameters.put("mch_appid", Variables.wxAppId);
        parameters.put("mchid", Variables.wxMchID);
        parameters.put("partner_trade_no", partner_trade_no);
        parameters.put("nonce_str", nonce_str);
        parameters.put("openid", user.getOpendId());
        parameters.put("check_name", checkName);
        parameters.put("amount", moneyD);
        parameters.put("spbill_create_ip", Variables.ip);
        parameters.put("desc", desc);
        String sign = XMLUtils.createSign(Variables.CHARSET, parameters);
        log.info("sign==========>>>>"+sign);
        Map map = new HashMap();
        map.put("amount",moneyD);
        map.put("check_name",checkName);
        map.put("desc",desc);
        map.put("mch_appid",Variables.wxAppId);
        map.put("mchid",Variables.wxMchID);
        map.put("nonce_str",nonce_str);
        map.put("openid",user.getOpendId());
        map.put("partner_trade_no",partner_trade_no);
        map.put("sign",sign);
        map.put("spbill_create_ip",Variables.ip);
        StringBuffer sb = new StringBuffer();
        sb.append("<xml>");
        XMLUtils.mapToXMLTest2(map, sb);
        sb.append("</xml>");
        log.info((new StringBuilder()).append("上送的数据为+++++++").append(sb.toString()).toString());
        try {
            CloseableHttpResponse response = HttpUtils.Post(
                    "https://api.mch.weixin.qq.com/mmpaymkttransfers/promotion/transfers", sb.toString(), true);
            String transfersXml = EntityUtils.toString(response.getEntity(), Variables.CHARSET);
            Map<String, String> transferMap = XMLUtils.xmlString2Map(transfersXml);
            log.info("微信转账回返信息=============>>>>>>"+transferMap.toString());
            BigDecimal subtract = BigDecimal.ZERO;
            if (transferMap.size()>0) {
                if (transferMap.get("result_code").equals("SUCCESS") && transferMap.get("return_code").equals("SUCCESS")) {
                    //成功需要进行的逻辑操作，
                    returnResult.setMessage("企业转账成功");
                    returnResult.setStatus(Boolean.TRUE);
                    if (changeMoney.getNote().contains("income")) {
                        subtract = ResultJSONUtils.updateUserMoney(user.getIncome(), changeMoney.getMoney(), "");
                        payService.updateOutIncome(user.getId(),subtract);
                    } else if (changeMoney.getNote().contains("mIncome")) {
                        subtract = ResultJSONUtils.updateUserMoney(user.getMIncome(), changeMoney.getMoney(), "");
                        payService.updateMIncome(user.getId(),subtract);
                    }
                    payService.updateChangeMoneyStatus(subtract,transferMap.get("result_code"), "微信转账成功", "10B", changeMoney.getId());
                    returnResult.setMessage("微信提现成功！");
                    returnResult.setStatus(Boolean.TRUE);
                    return ResultJSONUtils.getJSONObjectBean(returnResult);
                } else {
                    //失败原因
                    returnResult.setMessage("企业转账失败");
                    payService.updateChangeMoneyStatus(subtract,transferMap.get("err_code_des"), transferMap.get("return_msg"), "10C", changeMoney.getId());
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            throw MyExceptionUtils.mxe("企业付款异常" + e.getMessage());
        }
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    //opendId保存到个人信息里面
    @ResponseBody
    @RequestMapping("/saveUserInfo")
    @LoginRequired
    public JSONObject saveUserInfo(@CurrentUser AppUser user,String tradeType,String zfbNumber,String zfbRealName,String code) {
        log.info("/pay/saveUserInfo -----> opendId保存到个人信息里面==========>>>");
        ReturnResult returnResult = new ReturnResult();
        if (StringUtils.isEmpty(tradeType)) {
            returnResult.setMessage("提现类型不能为空！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }

        if("TXZFB".equals(tradeType)){
            if (StringUtils.isEmpty(zfbNumber) && StringUtils.isEmpty(zfbRealName)){
                returnResult.setMessage("支付宝授权信息为空！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
            loginService.updateUserByZFB(user.getId(),zfbNumber,zfbRealName);
            returnResult.setMessage("绑定支付宝信息成功！");
            returnResult.setStatus(Boolean.TRUE);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if ("TXWX".equals(tradeType)) {
            if (StringUtils.isEmpty(code)){
                returnResult.setMessage("code不能为空！！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
            String opendId = "";
            String wechatName = "";
            JSONObject userInfo = new JSONObject();
            try {
//          获取个人信息
                userInfo = getUserInfo(getOpenId(code));
                opendId = userInfo.getString("openid");
                wechatName = userInfo.getString("nickname");
                loginService.updateAppUser(user.getId(),"","","","","","", "",
                        "", "","","","","","","","",
                        "",opendId,wechatName,"");
                returnResult.setMessage("获取openid成功！");
                returnResult.setStatus(Boolean.TRUE);
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            } catch (Exception e) {
                log.info("获取openid失败: ===>>>"+e.getMessage());
                returnResult.setMessage("获取openid失败！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
        }
        returnResult.setMessage("保存类型选择错误！！");
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    //删除opendId
    @ResponseBody
    @RequestMapping("/deleteOpendId")
    @LoginRequired
    public JSONObject deleteOpendId(@CurrentUser AppUser user,String tradeType) {
        log.info("/pay/deleteOpendId -----> 删除opendId==========>>>");
        ReturnResult returnResult = new ReturnResult();
        if (StringUtils.isEmpty(tradeType)){
            returnResult.setMessage("类型不能为空！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }

        if("ZFB".equals(tradeType)){
            if (StringUtils.isEmpty(user.getZfbNumber()) && StringUtils.isEmpty(user.getZfbRealName())){
                returnResult.setMessage("支付宝授权信息为空！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
            String zfbNumber = "";
            String zfbRealName = "";
            loginService.updateZFBMessage(user.getId(),zfbNumber,zfbRealName);
            returnResult.setMessage("解绑成功！");
            returnResult.setStatus(Boolean.TRUE);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if ("WX".equals(tradeType)) {
            String opendId = "";
            String wechatName = "";
            try {
                loginService.updateOpendId(user.getId(),opendId,wechatName);
                returnResult.setMessage("解绑成功！");
                returnResult.setStatus(Boolean.TRUE);
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            } catch (Exception e) {
                log.info("解绑失败: ===>>>"+e.getMessage());
                returnResult.setMessage("解绑失败！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
        }
        returnResult.setMessage("解绑类型错误！！");
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 获取用户基本信息
     * @return
     */
    private JSONObject getUserInfo(JSONObject jsonObject){
        log.info("获取用户基本信息 --> getUserInfo: ===>>>");
        String accessToken = jsonObject.getString("access_token");
        String openid = jsonObject.getString("openid");
//         GET
//        https://api.weixin.qq.com/cgi-bin/user/info?access_token=ACCESS_TOKEN&openid=OPENID&lang=zh_CN
//        https://api.weixin.qq.com/sns/userinfo?access_token=ACCESS_TOKEN&openid=OPENID&lang=zh_CN
        String url = "https://api.weixin.qq.com/sns/userinfo?"
                + "access_token="+ accessToken
                + "&openid="+ openid
                + "&lang=zh_CN";
        String result = HttpUtils.getReturnData(url,Variables.CHARSET);
        JSONObject json = JSON.parseObject(result);
        log.info("获取用户基本信息======>>>>>"+json);
        return json;
    }

    /**
     * 获取用户openID
     * @Author  yuhao
     * @param code
     * @return  String
     * @Date	2018年9月3日
     */
    public JSONObject getOpenId(String code){
        log.info("获取用户openID --> code: ===>>>"+code);
        JSONObject jsonObject = new JSONObject();
        if (code != null) {
            String url = "https://api.weixin.qq.com/sns/oauth2/access_token?"
                    + "appid="+ Variables.wxAppId
                    + "&secret="+ Variables.APP_SECRET
                    + "&code=" + code + "&grant_type=authorization_code";
            String returnData = HttpUtils.getReturnData(url,Variables.CHARSET);
            log.info("用户授权====>>>>"+returnData);
            try {
                jsonObject = JSONObject.parseObject(returnData);
            } catch (Exception e) {
                jsonObject = JSONObject.parseObject(returnData);
                String errcode = jsonObject.getString("errcode");
                log.info("获取openid失败: ===>>>"+errcode);
            }
            log.info("openid======>>>>>"+jsonObject.getString("openid"));
        }
        return jsonObject;
    }

    /**
     * 单笔提现到支付宝账户
     * https://doc.open.alipay.com/docs/doc.htm?spm=a219a.7629140.0.0.54Ty29&treeId=193&articleId=106236&docType=1
     */
    public synchronized JSONObject outZFB(ChangeMoney changeMoney,AppUser user) {
        ReturnResult returnResult = new ReturnResult();
//        (1、ALIPAY_USERID：支付宝账号对应的支付宝唯一用户号。以2088开头的16位纯数字组成。2、ALIPAY_LOGONID：支付宝登录号，支持邮箱和手机号格式。)
        String remark = "单笔转账到支付宝";
        try {
            //手续费0.75%
            BigDecimal rate = changeMoney.getMoney().multiply(new BigDecimal(0.0075)).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal rateMoney = changeMoney.getMoney().subtract(rate).setScale(2, BigDecimal.ROUND_HALF_UP);

            AlipayFundTransToaccountTransferRequest request = new AlipayFundTransToaccountTransferRequest();
            request.setBizContent("{\"out_biz_no\":\""+ changeMoney.getId() +"\","
                    +"\"remark\":\""+ remark +"\","    //备注
                    +"\"payee_account\":\""+ changeMoney.getMoneyNumber() +"\","//支付宝账号
                    +"\"amount\":\""+ rateMoney.toString() +"\"," //金额
                    +"\"payer_show_name\":\""+ Variables.info +"\","   //转款账号
                    +"\"payee_real_name\":\""+ changeMoney.getRealName() +"\"," //支付宝真实姓名
                    + "\"payee_type\":\"ALIPAY_LOGONID\"}");
            AlipayFundTransToaccountTransferResponse response = Variables.alipayClient.execute(request);
            log.info("转账信息=======>"+response.getBody());
            BigDecimal subtract = BigDecimal.ZERO;
            if (response.isSuccess()) {
                JSONObject jsonObject = JSONObject.parseObject(response.getBody()).getJSONObject("alipay_fund_trans_toaccount_transfer_response");
                String msg = jsonObject.getString("msg");
                String code = jsonObject.getString("code");
                String outNo = jsonObject.getString("out_biz_no");

                if (changeMoney.getNote().contains("income")) {
                    subtract = ResultJSONUtils.updateUserMoney(user.getIncome(), changeMoney.getMoney(), "");
                    payService.updateOutIncome(user.getId(),subtract);
                } else if (changeMoney.getNote().contains("mIncome")) {
                    subtract = ResultJSONUtils.updateUserMoney(user.getMIncome(), changeMoney.getMoney(), "");
                    payService.updateMIncome(user.getId(),subtract);
                }
                payService.updateChangeMoneyStatus(subtract,msg, "支付宝提现成功！", "10B", changeMoney.getId());
                returnResult.setMessage("支付宝提现成功！");
                returnResult.setStatus(Boolean.TRUE);
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            } else {
                payService.updateChangeMoneyStatus(subtract,"ERROR", "支付宝提现失败！", "10C", changeMoney.getId());
                returnResult.setResult(response.getBody());
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.info("转账失败异常=======>"+e.getMessage());
            returnResult.setMessage("支付宝转账失败！");
        }
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 扫码支付
     * @param order
     * @return
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping("/payNative")
    @LoginRequired
    public JSONObject payNative(@CurrentUser AppUser user,Order order,HttpServletRequest request, HttpServletResponse response) throws Exception {
        getParameterMap(request, response);
        ReturnResult returnResult = new ReturnResult();
        log.info("/pay/payNative  ---扫码支付----创建扫码订单-----------");
        if (StringUtils.isEmpty(order.getTradeType())) {
            returnResult.setMessage("充值类型不能为空！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        order.setOrderNo("YYSM" + RandomSaltUtil.randomNumber(14));
        order.setStatus("10A");
        order.setMobile(user.getPhone());
        order.setMerchantId(user.getId());
//        order.setTradeType("SMWX");
//        order.setMoney("100");
        createOrder(order);
        if (StringUtils.isEmpty(order.getId())) {
            returnResult.setMessage("创建订单失败！缺少参数！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        if (order.getTradeType().contains("WX")) {
            return payNativeWX(order);
        } else if (order.getTradeType().contains("ZFB")) {
            return payNativeZFB(order,request,response);
        }
        returnResult.setMessage("扫码支付类型选择错误！！");
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    private JSONObject payNativeZFB(Order order,HttpServletRequest request, HttpServletResponse httpResponse) {
        getParameterMap(request, httpResponse);
        ReturnResult returnResult = new ReturnResult();
        log.info("/pay/payNativeZFB  ---->支付宝扫码回调");
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();//创建API对应的request
        alipayRequest.setReturnUrl(Variables.AliPayReturnUrl);//同步通知页面
        alipayRequest.setNotifyUrl(Variables.AliPayNotifyUrl);//在公共参数中设置回跳和通知地址
        String moneyD = order.getMoney().setScale(2, BigDecimal.ROUND_HALF_UP).toString();
        alipayRequest.setBizContent("{\"out_trade_no\":\""+ order.getId() +"\","
                +"\"total_amount\":\""+ moneyD +"\","
                +"\"subject\":\""+ Variables.subject +"\","
                +"\"body\":\""+ Variables.subject +"\","
                +"\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");//填充业务参数
        String form="";
        try {
            form = Variables.alipayClient.pageExecute(alipayRequest).getBody(); //调用SDK生成表单
            log.info("支付宝扫码返回结果====>>>>>"+form);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
//        httpResponse.getWriter().println(head+form+buttom);
//        httpResponse.reset();//进行刷新

//        写到html
//        httpResponse.setContentType("text/html;charset=" + CHARSET);
//        httpResponse.getWriter().write(form);//直接将完整的表单html输出到页面
//        httpResponse.reset();//进行刷新
//        ----------重复刷新问题
//        httpResponse.getWriter().flush();
//        httpResponse.getWriter().close();
        returnResult.setStatus(Boolean.TRUE);
        returnResult.setResult(form);
        if (form == null) {
            log.error("订单" + order.getId() + "未成功获取支付宝付款界面！");
            returnResult.setMessage("未成功获取支付宝付款界面！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        returnResult.setMessage(order.getId());
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    private JSONObject payNativeWX(Order order) {
        ReturnResult returnResult = new ReturnResult();
        HashMap<String, String> paramMap = Maps.newHashMap();
        try {
            paramMap.put("trade_type", "NATIVE"); //交易类型
            paramMap.put("spbill_create_ip",Variables.ip); //本机的Ip
            paramMap.put("product_id", "WX"+RandomSaltUtil.generetRandomSaltCode(30));  // 商户根据自己业务传递的参数 必填
            paramMap.put("body", Variables.subject);         //描述
            paramMap.put("out_trade_no", order.getId()); //商户 后台的贸易单号
            String moneyD = order.getMoney().setScale(2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100))
                    .setScale(0,BigDecimal.ROUND_HALF_UP).toString();
            paramMap.put("total_fee", moneyD); //金额必须为整数  单位为分
            paramMap.put("notify_url", Variables.wxNotifyUrl); //支付成功后，回调地址
            paramMap.put("appid", Variables.wxAppId); //appid
            paramMap.put("mch_id", Variables.wxMchID); //商户号
            paramMap.put("nonce_str", RandomSaltUtil.generetRandomSaltCode(32));  //随机数
            String sign = MD5Utils.signDatashwx(paramMap, Variables.wxKEY);
            paramMap.put("sign",sign);//根据微信签名规则，生成签名
            StringBuffer sb = new StringBuffer();
            sb.append("<xml>");
            XMLUtils.mapToXMLTest2(paramMap, sb);
            sb.append("</xml>");
            log.info((new StringBuilder()).append("上送的数据为+++++++").append(sb.toString()).toString());
            String resXml = XMLUtils.doPost("https://api.mch.weixin.qq.com/pay/unifiedorder", sb.toString(), Variables.CHARSET, "application/json");
            log.info("返回的数据为--------------------------+++++++" + resXml);
            Map ValidCard = XMLUtils.xmlString2Map(resXml);
            ValidCard.put("out_trade_no",order.getId());
            Map maps = new HashMap();
            String timestamp = String.valueOf((new Date()).getTime() / 1000L);
            maps.put("appid", ValidCard.get("appid").toString());
            maps.put("mch_id", Variables.wxMchID);
            maps.put("prepayid", ValidCard.get("prepay_id"));
            maps.put("package", "Sign=WXPay");
            maps.put("noncestr", ValidCard.get("nonce_str"));
            maps.put("timestamp", timestamp);
            //maps.put("signType", "MD5");
            String signs = MD5Utils.signDatashwx(maps, Variables.wxKEY);
            maps.put("sign", signs);
            //        return JSONObject.toJSONString(maps);
            returnResult.setMessage("返回成功！");
            returnResult.setStatus(Boolean.TRUE);
            returnResult.setResult(JSONObject.toJSON(ValidCard));
        } catch (Exception e) {
            e.printStackTrace();
            log.info("支付失败！参数不对！");
            returnResult.setMessage("支付失败！参数不对！");
            payService.updateStatus(order.getId(), "10C");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        returnResult.setMessage(order.getId());
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * APP浏览器支付
     * @param order
     * @return
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping("/payWapAPP")
    @LoginRequired
    public JSONObject payWapAPP(@CurrentUser AppUser user,Order order, HttpServletRequest request, HttpServletResponse response) throws Exception {
        getParameterMap(request, response);
        ReturnResult returnResult = new ReturnResult();
        log.info("pay/payWapAPP    ---APP浏览器支付----创建APP浏览器支付订单-----------");
        if (StringUtils.isEmpty(order.getTradeType())) {
            returnResult.setMessage("充值类型不能为空！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        order.setOrderNo("YYWAP" + RandomSaltUtil.randomNumber(14));
        order.setStatus("10A");
        order.setMobile(user.getPhone());
        order.setMerchantId(user.getId());
//        order.setTradeType("SMWX");
//        order.setMoney("100");
        createOrder(order);
        if (StringUtils.isEmpty(order.getId())) {
            returnResult.setMessage("创建订单失败！缺少参数！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        if (order.getTradeType().contains("WX")) {
            return payWapWX(order);
        } else if (order.getTradeType().contains("ZFB")) {
            return payWapZFB(order,request,response);
        }
        returnResult.setMessage("APP浏览器支付类型选择错误！！");
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    private JSONObject payWapWX(Order order) {
        ReturnResult returnResult = new ReturnResult();
        HashMap<String, String> paramMap = Maps.newHashMap();
        try {
            paramMap.put("trade_type", "MWEB"); //交易类型
            paramMap.put("spbill_create_ip",Variables.ip); //本机的Ip
            paramMap.put("body", Variables.subject);         //描述
            paramMap.put("out_trade_no", order.getId()); //商户 后台的贸易单号
            String moneyD = order.getMoney().setScale(2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100))
                    .setScale(0,BigDecimal.ROUND_HALF_UP).toString();
            paramMap.put("total_fee", moneyD); //金额必须为整数  单位为分
            paramMap.put("notify_url", Variables.wxNotifyUrl); //支付成功后，回调地址
            paramMap.put("appid", Variables.wxAppId); //appid
            paramMap.put("mch_id", Variables.wxMchID); //商户号
            paramMap.put("nonce_str", RandomSaltUtil.generetRandomSaltCode(32));  //随机数
            //场景信息
            String sceneInfo = "{\"h5_info\": {\"type\":\"Wap\","
                    +"\"wap_url\":\"" +Variables.ip_home
                    +"\",\"wap_name\":\""+Variables.info+"\"}}";
            paramMap.put("scene_info", sceneInfo);
            String sign = MD5Utils.signDatashwx(paramMap, Variables.wxKEY);
            paramMap.put("sign",sign);//根据微信签名规则，生成签名
            StringBuffer sb = new StringBuffer();
            sb.append("<xml>");
            XMLUtils.mapToXMLTest2(paramMap, sb);
            sb.append("</xml>");
            log.info((new StringBuilder()).append("上送的数据为+++++++").append(sb.toString()).toString());
            String resXml = XMLUtils.doPost("https://api.mch.weixin.qq.com/pay/unifiedorder", sb.toString(), Variables.CHARSET, "application/json");
            log.info("返回的数据为--------------------------+++++++" + resXml);
            Map ValidCard = XMLUtils.xmlString2Map(resXml);
            ValidCard.put("out_trade_no",order.getId());
            Map maps = new HashMap();
            String timestamp = String.valueOf((new Date()).getTime() / 1000L);
            maps.put("appid", ValidCard.get("appid").toString());
            maps.put("mch_id", Variables.wxMchID);
            maps.put("prepayid", ValidCard.get("prepay_id"));
            maps.put("package", "Sign=WXPay");
            maps.put("noncestr", ValidCard.get("nonce_str"));
            maps.put("timestamp", timestamp);
            //maps.put("signType", "MD5");
            String signs = MD5Utils.signDatashwx(maps, Variables.wxKEY);
            maps.put("sign", signs);
            //        return JSONObject.toJSONString(maps);

            //确认支付过后跳的地址,需要经过urlencode处理
            String urlString = URLEncoder.encode("http://www.heyuannetwork.com/isLogin/personalCenter/myConsumption", "GBK");
            String mweb_url = ValidCard.get("mweb_url")+"&redirect_url="+urlString;
            ValidCard.put("mweb_url",mweb_url);

            returnResult.setMessage("返回成功！");
            returnResult.setStatus(Boolean.TRUE);
            returnResult.setResult(JSONObject.toJSON(ValidCard));
        } catch (Exception e) {
            e.printStackTrace();
            log.info("支付失败！参数不对！");
            returnResult.setMessage("支付失败！参数不对！");
            payService.updateStatus(order.getId(), "10C");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        returnResult.setMessage("调用APP浏览器微信成功！！");
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    public JSONObject payWapZFB(Order order,HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        getParameterMap(httpRequest, httpResponse);
        ReturnResult returnResult = new ReturnResult();
        AlipayTradeWapPayRequest alipayRequest = new AlipayTradeWapPayRequest();//创建API对应的request
        alipayRequest.setReturnUrl(Variables.AliPayReturnUrl);//同步通知页面
        alipayRequest.setNotifyUrl(Variables.AliPayNotifyUrl);//同步通知页面
        String moneyD = order.getMoney().setScale(2, BigDecimal.ROUND_HALF_UP).toString();
        alipayRequest.setBizContent("{\"out_trade_no\":\""+ order.getId() +"\","
                +" \"total_amount\":\""+moneyD+"\","
                +" \"subject\":\""+Variables.subject+"\","
                +" \"product_code\":\"QUICK_WAP_PAY\"}");//填充业务参数
        String form="";
        try {
            form = Variables.alipayClient.pageExecute(alipayRequest).getBody(); //调用SDK生成表单
            log.info("支付宝APP浏览器返回结果====>>>>>"+form);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        returnResult.setStatus(Boolean.TRUE);
        returnResult.setResult(form);
        if (form == null) {
            log.error("订单" + order.getId() + "未成功获取支付宝付款界面！");
            returnResult.setMessage("未成功获取支付宝付款界面！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        returnResult.setMessage(order.getId());
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 支付退款，原路返回
     * @param httpRequest
     * @param httpResponse
     * @return
     */
    @RequestMapping("/refundMoney")
    @ResponseBody
    @LoginRequired
    public JSONObject refundMoney(@CurrentUser AppUser appUser,String orderItemId
            ,HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws Exception{
        getParameterMap(httpRequest, httpResponse);
        ReturnResult returnResult = new ReturnResult();
        log.info("/pay/refundMoney---支付退款，原路返回----创建退款订单-----------");
        if (StringUtils.isEmpty(orderItemId)){
            returnResult.setMessage("退款orderItemId不可以为空！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        OrderItemVo mallOrderItemById = mallShopService.getMallOrderItemById(orderItemId);
        if (StringUtils.isNull(mallOrderItemById)) {
            returnResult.setMessage("没有查询到该订单!");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }

        BigDecimal money = mallOrderItemById.getShopIncome();
        log.info("-------money-----------"+money);
        Order oldOrder = payService.getOrderId(mallOrderItemById.getOrderId());
        if (StringUtils.isNull(oldOrder)) {
            returnResult.setMessage("没有查询到该订单!");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (!"10B".equals(oldOrder.getStatus())) {
            returnResult.setMessage("该订单没有完成支付，不可以退款!");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        String tradeType = oldOrder.getTradeType();
        log.info("-------tradeType-----------"+tradeType);
        if (money == null || money.compareTo(BigDecimal.ZERO)==0){
            returnResult.setMessage("退款金额不能为空！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (appUser.getMIncome().compareTo(money) == -1){
            returnResult.setMessage("账户余额不足！请线下交易！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (money.compareTo(new BigDecimal(5001))==1){
            returnResult.setMessage("退款不能高于5000元！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (money.compareTo(new BigDecimal(1))==-1){
            returnResult.setMessage("退款不能低于1元！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        } else if (StringUtils.isEmpty(tradeType)) {
            returnResult.setMessage("退款类型无法获取！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        if (tradeType.contains("WX")) {
            tradeType = "TKWX";
        } else {
            tradeType = "TKZFB";
        }

        ChangeMoney changeTime = payService.getChangeMoneyByTime(appUser.getId());
        if (StringUtils.isNotNull(changeTime)) {
            //上次退款时间
            Date parse = dateFormat.parse(changeTime.getCreateTime());
            long goTime = parse.getTime()/ 1000;
            log.info("上次退款时间====>>>>"+dateFormat.format(parse)+"秒数====>>>>"+goTime);
            //当前系统时间
            Date date = dateFormat.parse(dateFormat.format(new Date()));
            Long toTime = date.getTime()/ 1000;
            log.info("当前系统时间====>>>>"+dateFormat.format(date)+"秒数====>>>>"+toTime);
            if ((toTime - goTime) < 30 ) {
                returnResult.setMessage("您好！30秒内，只能退款一次！");
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
        }

        ChangeMoney oldChangeMoney = myService.getChangeMoney("", orderItemId);
        if (StringUtils.isNotNull(oldChangeMoney)) {
            returnResult.setMessage("该订单已经退款成功，请勿重复退款！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }

        ChangeMoney changeMoney = new ChangeMoney();
        changeMoney.setNote("mIncome");
        //小订单ID
        changeMoney.setMoneyNumber(orderItemId);
        //支付回调ID
        changeMoney.setRealName(oldOrder.getResponseCode());
        changeMoney.setMoney(money);
        changeMoney.setTradeType(tradeType);
        changeMoney.setMerchantId(appUser.getId());
        changeMoney.setMobile(appUser.getPhone());
        changeMoney.setChangeNo("YY"+tradeType+ RandomSaltUtil.randomNumber(14));
        changeMoney.setHistoryMoney(appUser.getMIncome());
        if ("10B".equals(changeMoney.getStatus())) {
            returnResult.setMessage("请勿重复点击！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        changeMoney.setStatus("10B");
        changeMoney.setSourceId(oldOrder.getMerchantId());
        createShouMoney(changeMoney);
        if (StringUtils.isEmpty(changeMoney.getId())) {
            returnResult.setMessage("创建提现订单失败！缺少参数！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        if (tradeType.contains("WX")) {
            return refundWX(appUser,changeMoney,oldOrder,httpRequest,httpResponse);
        } else if (tradeType.contains("ZFB")) {
            return refundZFB(appUser,changeMoney,oldOrder,httpRequest,httpResponse);
        }
        returnResult.setMessage("退款类型选择错误！！");
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 作用：微信退款，原路返回<br>
     * 场景：当交易发生之后一段时间内，由于买家或者卖家的原因需要退款时，卖家可以通过退款接口将支付款退还给买家，
     * 微信支付将在收到退款请求并且验证成功之后，按照退款规则将支付款按原路退到买家帐号上。
     * 接口文档地址：https://pay.weixin.qq.com/wiki/doc/api/wxa/wxa_api.php?chapter=9_4
     * @throws Exception e
     * @param httpRequest
     * @param httpResponse
     * @return
     */
    public JSONObject refundWX(AppUser user,ChangeMoney changeMoney,Order oldOrder,HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws Exception {
        getParameterMap(httpRequest, httpResponse);
        log.info("/pay/refundWX ------>微信退款，原路返回 ");
        ReturnResult returnResult = new ReturnResult();
        try{
            String refundReason = "微信退款";
            log.info("oldOrder.getId()=======>>>>>"+oldOrder.getId());
            log.info("changeMoney.getResponseCode()=======>>>>>"+changeMoney.getResponseCode());
            Map<String, String> data = new HashMap<>();
            data.put("appid", Variables.wxAppId);
            data.put("mch_id", Variables.wxMchID);
            data.put("nonce_str", RandomSaltUtil.generetRandomSaltCode(32));
            // 变量名      字段名 必填  类型  示例值 描述
            // 微信订单号    二选一 String(32)  1.21775E+27 微信生成的订单号，在支付通知中有返回
            data.put("transaction_id", changeMoney.getResponseCode());
            // 商户订单号    String(32)  1.21775E+27 商户系统内部订单号，要求32个字符内，只能是数字、大小写字母_-|*@ ，且在同一个商户号下唯一。
            data.put("out_trade_no", oldOrder.getId());
            // 商户退款单号   是   String(64)  1.21775E+27 商户系统内部的退款单号，商户系统内部唯一，只能是数字、大小写字母_-|*@ ，同一退款单号多次请求只退一笔。
            data.put("out_refund_no", changeMoney.getId());
            // 订单金额 是   Int 100 订单总金额，单位为分，只能为整数，详见支付金额
            String moneyD = oldOrder.getMoney().setScale(2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(0,BigDecimal.ROUND_HALF_UP).toString();
            data.put("total_fee", moneyD);
            // 退款金额 是   Int 100 退款总金额，订单总金额，单位为分，只能为整数，详见支付金额
            // 默认单位为分，系统是元，所以需要*100
            String refundMoney = changeMoney.getMoney().setScale(2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(0,BigDecimal.ROUND_HALF_UP).toString();
            data.put("refund_fee", refundMoney);
            // 退款原因 否   String(80)  商品已售完   若商户传入，会在下发给用户的退款消息中体现退款原因
            data.put("refund_desc", refundReason);
            // 退款结果通知url    否   String(256)
            //data.put("notify_url", notify_url);
            /** 以下参数为非必填参数 **/
            // 退款资金来源   否   String(30)  REFUND_SOURCE_RECHARGE_FUNDS    仅针对老资金流商户使用;REFUND_SOURCE_UNSETTLED_FUNDS---未结算资金退款（默认使用未结算资金退款）;REFUND_SOURCE_RECHARGE_FUNDS---可用余额退款
            data.put("refund_account", "REFUND_SOURCE_RECHARGE_FUNDS");
            String sign = MD5Utils.signDatashwx(data, Variables.wxKEY);
            data.put("sign", sign);
            StringBuffer sb = new StringBuffer();
            sb.append("<xml>");
            XMLUtils.mapToXMLTest2(data, sb);
            sb.append("</xml>");
            CloseableHttpResponse response = HttpUtils.Post("https://api.mch.weixin.qq.com/secapi/pay/refund", sb.toString(), true);
            String transfersXml = EntityUtils.toString(response.getEntity(), Variables.CHARSET);
            Map<String, String> transferMap = XMLUtils.xmlString2Map(transfersXml);
            log.info("微信退款回返信息=============>>>>>>"+transferMap.toString());

            BigDecimal subtract = BigDecimal.ZERO;
            if (transferMap.size()>0) {
                if (transferMap.get("result_code").equals("SUCCESS") && transferMap.get("return_code").equals("SUCCESS")) {
                    //商家减钱
                    subtract = ResultJSONUtils.updateUserMoney(user.getMIncome(), changeMoney.getMoney(), "");
                    //修改订单状态
                    mallShopService.upIsRefund(changeMoney.getMoneyNumber());
                    payService.updateMIncome(user.getId(),subtract);
                    payService.updateChangeMoneyStatus(subtract,transferMap.get("result_code"), "微信退款成功！", "10B", changeMoney.getId());

                    //    极光商家退款通知 : 9 (id,sourceId)
                    String id = user.getId();
                    String sourceId = changeMoney.getSourceId();
                    log.info("sourceId========>>>>>>"+sourceId+"====id==>>>>>>"+id);
                    Map<String, String> params = Maps.newHashMap();
                    params.put("id", id);
                    params.put("sourceId", sourceId);
                    GouldUtils.doPost(Variables.sendRefundUrl, params);
                    returnResult.setStatus(Boolean.TRUE);
                    returnResult.setMessage("微信原路返回成功！");
                }else{
                    payService.updateChangeMoneyStatus(subtract,"ERROR", "微信退款失败！", "10C", changeMoney.getId());
                    returnResult.setMessage("微信原路返回失败！");
                }
            }
        }catch (Exception e){
            log.error("微信原路返回出错啦！！！"+e);
            throw MyExceptionUtils.mxe("微信原路返回异常" + e.getMessage());
        }
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 支付宝退款，原路返回
     * @param httpRequest
     * @param httpResponse
     * @return
     */
    public JSONObject refundZFB(AppUser user,ChangeMoney changeMoney,Order oldOrder,HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws Exception {
        getParameterMap(httpRequest, httpResponse);
        ReturnResult returnResult = new ReturnResult();
        log.info("/pay/refundZFB   --------->   支付宝退款，原路返回");
        try{
            //实例化具体API对应的request类,类名称和接口名称对应,当前调用接口名称：alipay.trade.app.pay

            String refundReason = "支付宝退款";
            AlipayTradeRefundModel refundModel = new AlipayTradeRefundModel();
            log.info("oldOrder.getId()=======>>>>>"+oldOrder.getId());
            log.info("changeMoney.getResponseCode()=======>>>>>"+changeMoney.getResponseCode());
            refundModel.setOutTradeNo(oldOrder.getId());
            refundModel.setTradeNo(changeMoney.getResponseCode());
            refundModel.setRefundAmount(String.valueOf(changeMoney.getMoney()));
            refundModel.setRefundReason(refundReason);
//            request.setBizContent("{\"out_trade_no\":\"" + changeMoney.getId() + "\","
//                    +"\"trade_no\":\"" + changeMoney.getResponseCode() + "\","   //支付宝交易号
//                    +"\"refund_amount\":\"" + changeMoney.getMoney() + "\","
//                    +"\"refund_reason\":\""+refundReason+"\"," //可部分退款和全部退款
//                    +"\"refund_currency\":\"cny\","
//                    +"\"org_pid\":\"" + null + "\"}");
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            request.setBizModel(refundModel);
            AlipayTradeRefundResponse response = Variables.alipayClient.execute(request);
            log.info("response.getMsg()========>>>>>>"+response.getMsg()+"\n");
            log.info("response.getBody()========>>>>>>"+response.getBody());
            BigDecimal subtract = BigDecimal.ZERO;
            if (response.isSuccess()) {
                //商家减钱
                subtract = ResultJSONUtils.updateUserMoney(user.getMIncome(), changeMoney.getMoney(), "");
                //修改订单状态
                mallShopService.upIsRefund(changeMoney.getMoneyNumber());
                payService.updateMIncome(user.getId(),subtract);
                payService.updateChangeMoneyStatus(subtract,response.getMsg(), "支付宝退款成功！", "10B", changeMoney.getId());

                //    极光商家退款通知 : 9 (id,sourceId)
                String id = user.getId();
                String sourceId = changeMoney.getSourceId();
                log.info("sourceId========>>>>>>"+sourceId+"====id==>>>>>>"+id);
                Map<String, String> params = Maps.newHashMap();
                params.put("id", id);
                params.put("sourceId", sourceId);
                GouldUtils.doPost(Variables.sendRefundUrl, params);

                returnResult.setStatus(Boolean.TRUE);
                returnResult.setMessage("支付宝原路返回成功！");
            }else{
                payService.updateChangeMoneyStatus(subtract,"ERROR", "支付宝退款失败！", "10C", changeMoney.getId());
                returnResult.setMessage("支付宝原路返回失败！");
            }
        }catch (Exception e){
            log.error("支付宝原路返回出错啦！！！"+e);
            throw MyExceptionUtils.mxe("支付宝原路返回异常" + e.getMessage());
        }
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }
}
