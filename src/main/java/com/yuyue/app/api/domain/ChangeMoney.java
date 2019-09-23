package com.yuyue.app.api.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeMoney implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
//    消费编号
    private String changeNo;
//    商户ID
    private String merchantId;
//    交易类型
    private String tradeType;
//    交易金额
    private BigDecimal money;
//    手机号
    private String mobile;
//    交易状态
    private String status;
//    交易时间
    private String createTime;
//    完成时间
    private String completeTime;
//    备注
    private String note;
//    收礼物的那个人
    private String sourceId;

}
