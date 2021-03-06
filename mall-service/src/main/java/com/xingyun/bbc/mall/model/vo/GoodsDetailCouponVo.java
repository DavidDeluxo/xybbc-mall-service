package com.xingyun.bbc.mall.model.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@ApiModel("商品详情优惠券--点击")
@Data
public class GoodsDetailCouponVo implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("已领取优惠券")
    private List<CouponVo> receiveCouponLis;

    @ApiModelProperty("未领取优惠券")
    private List<CouponVo> unReceiveCouponLis;

    @ApiModelProperty("当前系统时间")
    private Date nowDate;




}
