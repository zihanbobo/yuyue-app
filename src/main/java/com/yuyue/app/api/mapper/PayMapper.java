package com.yuyue.app.api.mapper;

import com.yuyue.app.api.domain.Order;
import com.yuyue.app.api.domain.OutMoney;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PayMapper extends MyBaseMapper<Order> {

    @Transactional
    @Insert("insert into yuyue_order (id,orderNo,tradeType,money,mobile,status,statusCode,merchantId,note,sourceId)  values  " +
            "(#{id},#{orderNo},#{tradeType},#{money},#{mobile},#{status},#{statusCode},#{merchantId},#{note},#{sourceId})")
    @Options(useGeneratedKeys=true, keyProperty="id", keyColumn="id")
    void createOrder(Order order);

    @Select("SELECT *,DATE_FORMAT(COMPLETE_TIME ,'%Y-%m-%d %H:%i:%s') completeTime FROM yuyue_order b WHERE b.id = #{orderId} ")
    Order getOrderId(String orderId);

    @Transactional
    @Update("UPDATE yuyue_order b SET b.`status` = #{status} WHERE b.id = #{id} ")
    void updateStatus( @Param("id")String id,@Param("status") String status);

    @Transactional
    @Update("UPDATE yuyue_order b SET b.`status`=#{status},b.responseCode=#{responseCode},b.responseMessage=#{responseMessage} " +
            "WHERE b.orderNo = #{orderNo} ")
    void updateOrderStatus(@Param("responseCode") String responseCode,@Param("responseMessage") String responseMessage,
                           @Param("status") String status, @Param("orderNo") String orderNo);

    @Transactional
    @Update("UPDATE yuyue_merchant b SET b.TOTAL = b.TOTAL + #{money} WHERE b.ID = #{merchantId} ")
    void updateTotal(@Param("merchantId") String merchantId,@Param("money") BigDecimal money);

    @Transactional
    @Update("UPDATE yuyue_merchant b SET b.TOTAL = b.TOTAL - #{money} WHERE b.ID = #{merchantId} ")
    void sendMoney(@Param("merchantId") String merchantId,@Param("money") BigDecimal money);

    @Select("SELECT *,DATE_FORMAT(COMPLETE_TIME ,'%Y-%m-%d %H:%i:%s') completeTime FROM yuyue_order b WHERE b.merchantId = #{id} AND b.`status` = '10B' ")
    List<Order> getMoneyList(@Param("id") String id);

    @Transactional
    @Insert("insert into yuyue_out_money (id,outNo,tradeType,money,merchantId,responseCode,responseMessage,moneyNumber,realName)  values  " +
            " (#{id},#{outNo},#{tradeType},#{money},#{merchantId},#{responseCode},#{responseMessage},#{moneyNumber},#{realName})")
    @Options(useGeneratedKeys=true, keyProperty="id", keyColumn="id")
    void createOut(OutMoney outMoney);

    @Transactional
    @Update("UPDATE yuyue_out_money b SET b.`status`=#{status},b.responseCode=#{responseCode},b.responseMessage=#{responseMessage} "
            +" WHERE b.outNo = #{outNo} ")
    void updateOutStatus(String responseCode, String responseMessage, String status, String outNo);

    @Transactional
    @Update("UPDATE yuyue_merchant b SET b.income = b.income - #{money} WHERE b.ID = #{merchantId} ")
    void updateOutIncome(String merchantId, BigDecimal money);

    @Select("SELECT *,DATE_FORMAT(COMPLETE_TIME ,'%Y-%m-%d %H:%i:%s') completeTime FROM yuyue_out_money b WHERE b.merchantId = #{id} ")
    List<OutMoney> getOutMoneyList(@Param("id") String id);
}
