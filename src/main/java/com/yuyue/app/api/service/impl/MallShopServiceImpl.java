package com.yuyue.app.api.service.impl;

import com.yuyue.app.api.domain.*;
import com.yuyue.app.api.mapper.*;
import com.yuyue.app.api.service.MallShopService;
import com.yuyue.app.api.service.PayService;
import com.yuyue.app.utils.ResultJSONUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service(value = "mallShopServiceImpl")
public class MallShopServiceImpl implements MallShopService {

    @Autowired
    private MallShopMapper mallShopMapper;
    @Autowired
    private ShopImageMapper shopImageMapper;
    @Autowired
    private SpecificationMapper specificationMapper;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private MallCommentMapper commentMapper;
    @Autowired
    private MallOrderItemMapper mallOrderItemMapper;
    @Autowired
    private MallAddressMapper mallAddressMapper;
    @Autowired
    private LoginMapper loginMapper;
    @Autowired
    private PayService payService;

    @Override
    public MallShop getMyMallShop(String shopId) {
        return mallShopMapper.getMyMallShop(shopId);
    }

    @Override
    public List<MallShop> getMyMallShops(String merchantId) {
        return mallShopMapper.getMyMallShops(merchantId);
    }

    @Override
    public MallShop myMallShopInfo(String merchantId) {
        return mallShopMapper.myMallShopInfo(merchantId);
    }

    @Override
    public List<MallShop> getAllMallShop(String myArea,String content) {
        return mallShopMapper.getAllMallShop(myArea,content);
    }

    @Override
    public void insertMyMallShop(MallShop mallShop) {
        mallShopMapper.insertMyMallShop(mallShop);
    }

    @Override
    public void updateMyMallShopInfo(MallShop mallShop) {
        mallShopMapper.updateMyMallShopInfo(mallShop);
    }

    @Override
    public ShopImage getShopImage(String shopId) {
        return shopImageMapper.getShopImage(shopId);
    }

    @Override
    public void insertShopImage(ShopImage shopImage) {
        shopImageMapper.insertShopImage(shopImage);
    }

    @Override
    public void deleteShopImage(String imagePath) {
        shopImageMapper.deleteShopImage(imagePath);
    }

    @Override
    public List<Specification> getSpecification(String shopId) {
        return specificationMapper.getSpecification(shopId);
    }

    @Override
    public Specification getSpecificationById(String specificationId) {
        return specificationMapper.getSpecificationById(specificationId);
    }

    @Override
    public void insertSpecification(Specification specification) {
        specificationMapper.insertSpecification(specification);
    }

    @Override
    public void deleteSpecification(String specificationId) {
        specificationMapper.deleteSpecification(specificationId);
    }

    @Override
    public void updateSpecification(Specification specification) {
        specificationMapper.updateSpecification(specification);
    }

    @Override
    public List<Cart> getCarts(String cartId,String  consumerId) {
        return cartMapper.getCarts(cartId,consumerId);
    }

    @Override
    public Cart getCart(String commodityId,String consumerId) {
        return cartMapper.getCart(commodityId,consumerId);
    }

    @Override
    public void editCart(Cart cart) {
        cartMapper.addCart(cart);
    }

    @Override
    public void deleteCart(String cardId , String shopId) {
        cartMapper.deleteCart(cardId,shopId);
    }

    @Override
    public void deletePayCart(String consumerId, String commodityId) {
        cartMapper.deletePayCart(consumerId,commodityId);
    }

    @Override
    public List<MallComment> getMallComments(String shopId) {
        return commentMapper.getMallComments(shopId);
    }

    @Override
    public MallComment getMallComment(String shopId, String consumerId) {
        return commentMapper.getMallComment(shopId,consumerId);
    }

    @Override
    public double getScore(String shopId) {
        return commentMapper.getScore(shopId);
    }

    @Override
    public void addMallComment(MallComment mallComment) {
        commentMapper.addMallComment(mallComment);
    }

    @Override
    public List<OrderItem> getMallOrderItem(String orderId,String shopId,String status) {
        return mallOrderItemMapper.getMallOrderItem(orderId,shopId,status);
    }

    @Override
    public List<String> getOrderToItem(String shopId,String consumerId,String status) {
        return mallOrderItemMapper.getOrderToItem(shopId,consumerId,status);
    }

    @Override
    public void editMallOrderItem(OrderItem orderItem) {
        mallOrderItemMapper.editMallOrderItem(orderItem);
    }

    @Override
    public void updateOrderItemsStatus(String orderId, String status) {
        mallOrderItemMapper.updateOrderItemsStatus(orderId,status);
    }
    //减库存及给商家们加钱的方法及修改订单项状态
    @Override
    public void mallPaySuccess(String orderId) {

        //给商家们加钱的方法
        List<String> shopIds = mallOrderItemMapper.getShopIds(orderId);
        for (String shopId:shopIds
             ) {
            List<OrderItem> mallOrderItems = getMallOrderItem(orderId, shopId, "");
            String money = null;
            for (OrderItem orderItem:mallOrderItems
                 ) {
                //减库存操作
                Specification specification = new Specification();
                specification.setCommodityId(orderItem.getCommodityId());
                specification.setCommodityNum(orderItem.getCommodityNum());
                updateSpecification(specification);
                money = orderItem.getShopIncome().toString();
                updateOrderItemsStatus(orderId,"10B");
            }
            //给商家加钱
            MallShop myMallShop = getMyMallShop(shopId);
            String merchantId = myMallShop.getMerchantId();
            AppUser appUserMsg = loginMapper.getAppUserMsg("", "", merchantId,"");
            BigDecimal mIncome = ResultJSONUtils.updateMIncome(appUserMsg, new BigDecimal(money), "+");
            //修改订单项状态
            payService.updateMIncome(merchantId,mIncome);
        }

    }

    @Override
    public List<MallAddress> getMallAddrByUserId(String userId) {
        return mallAddressMapper.getMallAddrByUserId(userId);
    }

    @Override
    public MallAddress getDefaultAddress(String userId) {
        return mallAddressMapper.getDefaultAddress(userId);
    }

    @Override
    public MallAddress getMallAddress(String addressId) {
        return mallAddressMapper.getMallAddress(addressId);
    }

    @Override
    public void editMallAddr(MallAddress mallAddress) {
        mallAddressMapper.editMallAddr(mallAddress);
    }

    @Override
    public void deleteMallAddr(String addressId) {
        mallAddressMapper.deleteMallAddr(addressId);
    }

    @Override
    public void changeDefaultAddr(String userId) {
        mallAddressMapper.changeDefaultAddr(userId);
    }
}
