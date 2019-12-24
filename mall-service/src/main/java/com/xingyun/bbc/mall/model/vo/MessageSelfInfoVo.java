package com.xingyun.bbc.mall.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @Description 消息信息
 * @ClassName MessageSelfInfoVo
 * @Author ming.yiFei
 * @Date 2019/12/23 10:23
 **/
@Data
public class MessageSelfInfoVo {

    @ApiModelProperty(value = "商品ID")
    private Long goodsId;

    @ApiModelProperty(value = "sku ID")
    private Long skuId;

    @ApiModelProperty(value = "sku 数量")
    private Integer skuNum;

    @ApiModelProperty(value = "订单ID")
    private String orderId;

    @ApiModelProperty(value = "收件人姓名")
    private String deliveryName;

    @ApiModelProperty(value = "物流单号")
    private String orderLogisticsNo;

    @ApiModelProperty(value = "快递类型")
    private Integer logisticsType;

    @ApiModelProperty(value = "优惠券ID")
    private Long couponId;

    @ApiModelProperty(value = "物流轨迹节点内容")
    private String trajectoryContext;

    @ApiModelProperty(value = "物流轨迹节点时间")
    private String trajectoryTime;
}