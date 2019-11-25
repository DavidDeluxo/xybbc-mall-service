package com.xingyun.bbc.mallpc.service.impl;

import com.google.common.collect.Lists;
import com.xingyun.bbc.core.activity.api.CouponProviderApi;
import com.xingyun.bbc.core.activity.enums.CouponScene;
import com.xingyun.bbc.core.activity.model.dto.CouponReleaseDto;
import com.xingyun.bbc.core.enums.ResultStatus;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.market.api.CouponApi;
import com.xingyun.bbc.core.market.api.CouponReceiveApi;
import com.xingyun.bbc.core.market.enums.CouponReceiveStatusEnum;
import com.xingyun.bbc.core.market.enums.CouponReleaseTypeEnum;
import com.xingyun.bbc.core.market.enums.CouponStatusEnum;
import com.xingyun.bbc.core.market.enums.CouponTypeEnum;
import com.xingyun.bbc.core.market.po.Coupon;
import com.xingyun.bbc.core.market.po.CouponReceive;
import com.xingyun.bbc.core.query.Criteria;
import com.xingyun.bbc.core.utils.Result;

import com.xingyun.bbc.mallpc.common.components.lock.XybbcLock;
import com.xingyun.bbc.mallpc.common.constants.MallPcConstants;
import com.xingyun.bbc.mallpc.common.ensure.Ensure;
import com.xingyun.bbc.mallpc.common.exception.MallPcExceptionCode;
import com.xingyun.bbc.mallpc.common.utils.PageUtils;
import com.xingyun.bbc.mallpc.common.utils.PriceUtil;
import com.xingyun.bbc.mallpc.common.utils.RandomUtils;
import com.xingyun.bbc.mallpc.model.dto.coupon.MyCouponDto;
import com.xingyun.bbc.mallpc.model.dto.coupon.ReceiveCouponDto;
import com.xingyun.bbc.mallpc.model.vo.PageVo;
import com.xingyun.bbc.mallpc.model.vo.coupon.CouponVo;
import com.xingyun.bbc.mallpc.model.vo.coupon.MyCouponVo;
import com.xingyun.bbc.mallpc.service.MyCouponService;

import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class MyCouponServiceImpl implements MyCouponService {

    private static final Logger logger = LoggerFactory.getLogger(MyCouponService.class);

    @Autowired
    private CouponReceiveApi couponReceiveApi;

    @Autowired
    private CouponApi couponApi;

    @Autowired
    private PageUtils pageUtils;

    @Autowired
    private XybbcLock xybbcLock;

    @Autowired
    private CouponProviderApi couponProviderApi;

    /**
     * @author lll
     * @version V1.0
     * @Description: 获取我的优惠券列表
     * @Param: myCouponDto
     * @return: MyCouponVo                                                                                                                                                                                                                                                                 <                                                                                                                                                                                                                                                               GoodsCategoryVo>>
     * @date 2019/11/12 13:49
     */
    @Override
    public Result<MyCouponVo> getMyCouponVo(MyCouponDto myCouponDto) {
        //查询已经领到的优惠券信息
        Criteria<CouponReceive, Object> criteria = Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFuid, myCouponDto.getFuid());
        if (!StringUtils.isEmpty(myCouponDto.getFuserCouponStatus())) {
            criteria.andEqualTo(CouponReceive::getFuserCouponStatus, myCouponDto.getFuserCouponStatus());
        }

        Result<Integer> countResult = couponReceiveApi.countByCriteria(criteria);
        if (!countResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        criteria.fields(CouponReceive::getFcouponId, CouponReceive::getFvalidityStart, CouponReceive::getFvalidityEnd)
                .page(myCouponDto.getCurrentPage(), myCouponDto.getPageSize())
                .sortDesc(CouponReceive::getFcreateTime);
        Result<List<CouponReceive>> listResult = couponReceiveApi.queryByCriteria(criteria);
        if (!listResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        Integer count = countResult.getData();
        PageVo<CouponVo> couponPageVo = pageUtils.convert(count, listResult.getData(), CouponVo.class, myCouponDto);
        //查询优惠券信息
        for (CouponVo couponVo : couponPageVo.getList()) {
            Result<Coupon> couponResult = couponApi.queryOneByCriteria(Criteria.of(Coupon.class)
                    .andEqualTo(Coupon::getFcouponId, couponVo.getFcouponId())
                    .fields(Coupon::getFcouponName, Coupon::getFcouponType,
                            Coupon::getFthresholdAmount, Coupon::getFdeductionValue,
                            Coupon::getFvalidityType, Coupon::getFvalidityDays, Coupon::getFreleaseType));
            if (!couponResult.isSuccess()) {
                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
            }
            Coupon coupon = couponResult.getData();
            couponVo.setFcouponName(coupon.getFcouponName());
            couponVo.setFcouponType(coupon.getFcouponType());
            couponVo.setFthresholdAmount(PriceUtil.toYuan(coupon.getFthresholdAmount()));

            //优惠券类型，1满减券、2折扣券
            if (coupon.getFcouponType().equals(CouponTypeEnum.FULL_REDUCTION.getCode())) {
                couponVo.setFdeductionValue(PriceUtil.toYuan(coupon.getFdeductionValue()));
            } else {
                couponVo.setFdeductionValue(new BigDecimal(coupon.getFdeductionValue()).divide(new BigDecimal("10"), 1, BigDecimal.ROUND_HALF_UP));
            }
            couponVo.setFvalidityType(coupon.getFvalidityType());
            couponVo.setFvalidityDays(coupon.getFvalidityDays());
            couponVo.setFreleaseType(coupon.getFreleaseType());
        }
        //查询各种优惠券数量
        Integer fuserCouponStatus = myCouponDto.getFuserCouponStatus();

        MyCouponVo myCouponVo = new MyCouponVo();
        myCouponVo.setCouponVo(couponPageVo);
        myCouponVo.setUnUsedNum(fuserCouponStatus.equals(CouponReceiveStatusEnum.NOT_USED.getCode()) ? count
                : this.getCouponByStatus(myCouponDto, CouponReceiveStatusEnum.NOT_USED.getCode()));
        myCouponVo.setUsedNum(fuserCouponStatus.equals(CouponReceiveStatusEnum.USED.getCode()) ? count
                : this.getCouponByStatus(myCouponDto, CouponReceiveStatusEnum.USED.getCode()));
        myCouponVo.setExpiredNum(fuserCouponStatus.equals(CouponReceiveStatusEnum.INVALID.getCode()) ? count
                : this.getCouponByStatus(myCouponDto, CouponReceiveStatusEnum.INVALID.getCode()));
        myCouponVo.setNowDate(new Date());

        return Result.success(myCouponVo);
    }

    private Integer getCouponByStatus(MyCouponDto myCouponDto, Integer fuserCouponStatus) {
        Criteria<CouponReceive, Object> criteriaStatus = Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFuid, myCouponDto.getFuid())
                .andEqualTo(CouponReceive::getFuserCouponStatus, fuserCouponStatus);
        Result<Integer> countResult = couponReceiveApi.countByCriteria(criteriaStatus);
        if (!countResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        return countResult.getData();
    }


    /**
     * @author lll
     * @version V1.0
     * @Description: 领券中心领取优惠券
     * @Param: receiveCouponDto
     * @return: Result                                                                                                                                                                                                                                                                 <                                                                                                                                                                                                                                                               GoodsCategoryVo>>
     * @date 2019/11/12 13:49
     */
    @Override
    public Result addReceiveCoupon(Long fcouponId, Long fuid) {
        //校验入参
        if (null == fuid || null == fcouponId) {
            return Result.failure(MallPcExceptionCode.PARAM_ERROR);
        }
        //查询优惠券--状态（已发布）--类型（页面领取）--剩余数量--领取上限--有效期结束时间--发放结束时间
        Result<Coupon> couponResult = couponApi.queryOneByCriteria(Criteria.of(Coupon.class)
                .andEqualTo(Coupon::getFcouponId, fcouponId)
                .andEqualTo(Coupon::getFcouponStatus, CouponStatusEnum.PUSHED.getCode())
                .andEqualTo(Coupon::getFreleaseType, CouponReleaseTypeEnum.PAGE_RECEIVE.getCode())
                .fields(Coupon::getFperLimit, Coupon::getFsurplusReleaseQty, Coupon::getFvalidityType,
                        Coupon::getFvalidityEnd, Coupon::getFreleaseTimeEnd, Coupon::getFreleaseTimeType
                ));
        if (!couponResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        Coupon coupon = couponResult.getData();
        //校验该券是否存在
        if (null == coupon) {
            throw new BizException(MallPcExceptionCode.COUPON_IS_NOT_EXIST);
        }
        //校验该券库存
        if (coupon.getFsurplusReleaseQty() <= 0) {
            throw new BizException(MallPcExceptionCode.COUPON_IS_PAID_OUT);
        }
        Date now = new Date();
        //校验该券有效期时间
        if (coupon.getFvalidityType() == 1 && now.after(coupon.getFvalidityEnd())) {
            return Result.failure(MallPcExceptionCode.COUPON_IS_INVALID);
        }
        //校验该券领取时间
        if (coupon.getFreleaseTimeType() == 2 && now.after(coupon.getFreleaseTimeEnd())) {
            throw new BizException(MallPcExceptionCode.COUPON_IS_NOT_TIME);
        }
        //查询已经领到的券张数
        Result<Integer> countResult = couponReceiveApi.countByCriteria(Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFuid, fuid)
                .andEqualTo(CouponReceive::getFcouponId, fcouponId));
        if (!couponResult.isSuccess()) {
            logger.error("查询已经领到的券张数失败，userid{} couponId{}", fuid, fcouponId);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        //校验该券领取上限
        if (null != countResult.getData() && countResult.getData().equals(coupon.getFperLimit())) {
            throw new BizException(MallPcExceptionCode.COUPON_IS_MAX);
        }
        ReceiveCouponDto receiveCouponDto = new ReceiveCouponDto();
        receiveCouponDto.setFuid(fuid);
        receiveCouponDto.setFcouponId(fcouponId);
        Result result = this.receiveCenterCoupon(receiveCouponDto);
        return Result.success(result.getData());
    }


    /**
     * @author lll
     * @version V1.0
     * @Description: 调用远程服务领取优惠券
     * @Param: receiveCouponDto
     * @return: Result                                                                                                                                                                                                                                                                 <                                                                                                                                                                                                                                                               GoodsCategoryVo>>
     * @date 2019/11/12 13:49
     */
    @GlobalTransactional
    public Result receiveCenterCoupon(ReceiveCouponDto receiveCouponDto) {
        Long fcouponId = receiveCouponDto.getFcouponId();
        Long fuid = receiveCouponDto.getFuid();
        String fcouponCode = receiveCouponDto.getFcouponCode();
        if (null == fcouponId || null == fuid) {
            return Result.failure(MallPcExceptionCode.PARAM_ERROR);
        }
        //加分布式锁
        String lockKey = org.apache.commons.lang3.StringUtils.join(Lists.newArrayList(MallPcConstants.MALL_RECEIVE_COUPON, fcouponId, fuid), ":");
        String lockValue = RandomUtils.getUUID();
        try {
            Ensure.that(xybbcLock.tryLockTimes(lockKey, lockValue, 3, 6)).isTrue(MallPcExceptionCode.SYSTEM_BUSY_ERROR);
            //更新优惠券发放数量
            CouponReleaseDto couponReleaseDto = new CouponReleaseDto();
            couponReleaseDto.setCouponScene(CouponScene.PAGE_RECEIVE);
            couponReleaseDto.setCouponId(fcouponId);
            couponReleaseDto.setUserId(fuid);
            couponReleaseDto.setCouponCode(fcouponCode);
            couponReleaseDto.setAlreadyReceived(true);
            couponReleaseDto.setDeltaValue(-1);
            Result updateReleaseResult = couponProviderApi.updateReleaseQty(couponReleaseDto);
            Ensure.that(updateReleaseResult.isSuccess()).isTrue(new MallPcExceptionCode(updateReleaseResult.getCode(), updateReleaseResult.getMsg()));
            //调用领券服务
            Result receiveReceive = couponProviderApi.receive(couponReleaseDto);
            Ensure.that(receiveReceive.isSuccess()).isTrue(new MallPcExceptionCode(receiveReceive.getCode(), receiveReceive.getMsg()));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            xybbcLock.releaseLock(lockKey, lockValue);
        }
        return Result.success(true);
    }
}