package com.yuyue.app.api.service;

import com.yuyue.app.api.domain.*;

import java.math.BigDecimal;
import java.util.List;

public interface PayService {
    void createOrder(Order order);

    Order getOrderId(String orderId);

    List<Order> getGGOrder(String consumerId,String status);

    List<Order> getSCOrder(String consumerId,String status);

    void updateStatus(String id,String status);

    void updateOrderStatus(String responseCode, String responseMessage, String status,String orderno);

    void updateTotal(String merchantId, BigDecimal money);

    void createOut(OutMoney outMoney);

    void updateOutStatus(String code, String msg, String s, String outNo);

    void updateOutIncome(String merchantId, BigDecimal money);

    void updateMIncome(String merchantId, BigDecimal money);

    List<OutMoney> getOutMoneyList(String id, int begin, int size);

    List<Gift> getGiftList();

    Gift getGift(String id);

    void createShouMoney(ChangeMoney changeMoney);

    List<Order> findOrderList(String startTime);

    void updateChangeMoneyStatus(BigDecimal subtract,String responseCode, String responseMessage, String status, String id);

    //商户店家ID
    List<String> getShopUserList(String id);

    ChangeMoney getChangeMoneyByTime(String userId);
}
