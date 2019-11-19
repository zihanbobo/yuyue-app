package com.yuyue.app.api.mapper;

import com.yuyue.app.api.domain.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface MallOrderItemMapper extends MyBaseMapper<OrderItem> {



    List<OrderItem> getMallOrderItem(@Param(value = "orderId") String orderId,@Param(value = "shopId") String shopId,
                                     @Param(value = "status") String status);

    @Select("SELECT DISTINCT shop_id FROM yuyue_mall_order_item  \n" +
            "        WHERE 1=1 and order_id = #{orderId}")
    List<String> getShopIds(@Param(value = "orderId") String orderId);

    List<String> getOrderToItem(@Param(value = "shopId") String shopId,@Param(value = "consumerId") String consumerId,
                                  @Param(value = "status") String status);



    @Transactional
    @Insert("REPLACE INTO yuyue_mall_order_item (order_item_id,order_id,shop_id, \n" +
            "address_id,commodity_id,consumer_id,fare,commodity_price,shop_income,commodity_num,status) \n" +
            "VALUES \n" +
            "(#{orderItemId},#{orderId},#{shopId},#{addressId},#{commodityId},#{consumerId}, \n" +
            "#{fare},#{commodityPrice},#{shopIncome},#{commodityNum},#{status})")
    void editMallOrderItem(OrderItem orderItem);

    @Transactional
    @Update("update yuyue_mall_order_item  set status= #{status} WHERE order_id = #{orderId}")
    void updateOrderItemsStatus(@Param(value = "orderId") String orderId,@Param(value = "status") String status);
}
