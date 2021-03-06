package com.yuyue.app.api.mapper;

import com.yuyue.app.api.domain.Commodity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CommodityMapper extends MyBaseMapper<Commodity> {
    @Transactional
    @Insert("INSERT INTO yuyue_commodity (\n" +
            "\tCOMMODITY_ID,\n" +
            "\tORDER_ID,\n" +
            "\tPRICE_ID," +
            "\tCATEGORY,\n" +
            "\tCOMMODITY_NAME,\n" +
            "\tAD_WORD,\n" +
            "\tAD_IMAGE_URL,\n" +
            "\tCOMMODITY_PRICE,\n" +
            "\tPAY_URL,\n" +
            "\tADDR,\n" +
            "\tSTATUS,\n" +
            "\tSPOKESPERSON_ID,\n" +
            "\tMERCHANT_ID\n" +
            ")  VALUES \n" +
            "(#{commodityId},#{orderId},#{priceId},#{category},#{commodityName},#{adWord},#{adImageUrl},#{commodityPrice}," +
            "#{payUrl},#{addr},#{status},#{spokesPersonId},#{merchantId})")
    void commodityToSpread(Commodity commodity);


    List<Commodity> getCommodityInfo(@Param("merchantId")String merchantId,@Param("videoId")String videoId,@Param(value = "commodityId")String commodityId,@Param(value = "begin")int begin,@Param(value = "limit")int limit);

    @Transactional
    @Update("UPDATE yuyue_commodity SET `STATUS`= #{status} WHERE COMMODITY_ID = #{commodityId}")
    void updateCommodityStatus(@Param("commodityId")String commodityId,@Param("status")String status);
}
