package com.xingyun.bbc.mall.service.impl;


import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.xingyun.bbc.activity.api.CouponProviderApi;
import com.xingyun.bbc.activity.enums.CouponScene;
import com.xingyun.bbc.activity.model.dto.CouponQueryDto;
import com.xingyun.bbc.activity.model.dto.CouponReleaseDto;
import com.xingyun.bbc.activity.model.vo.CouponQueryVo;

import com.xingyun.bbc.core.enums.ResultStatus;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.market.api.CouponApi;
import com.xingyun.bbc.core.market.api.CouponCodeApi;
import com.xingyun.bbc.core.market.api.CouponReceiveApi;
import com.xingyun.bbc.core.market.enums.CouponReleaseTypeEnum;
import com.xingyun.bbc.core.market.enums.CouponStatusEnum;
import com.xingyun.bbc.core.market.enums.CouponTypeEnum;
import com.xingyun.bbc.core.market.po.Coupon;
import com.xingyun.bbc.core.market.po.CouponCode;
import com.xingyun.bbc.core.market.po.CouponReceive;
import com.xingyun.bbc.core.query.Criteria;
import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.mall.base.utils.PriceUtil;
import com.xingyun.bbc.mall.base.utils.RandomUtils;
import com.xingyun.bbc.mall.common.constans.MallConstants;
import com.xingyun.bbc.mall.common.ensure.Ensure;
import com.xingyun.bbc.mall.common.exception.MallExceptionCode;
import com.xingyun.bbc.mall.common.lock.XybbcLock;
import com.xingyun.bbc.mall.model.dto.QueryCouponDto;
import com.xingyun.bbc.mall.model.dto.ReceiveCouponDto;
import com.xingyun.bbc.mall.model.vo.ReceiveCenterCouponVo;
import com.xingyun.bbc.mall.service.GoodDetailService;
import com.xingyun.bbc.mall.service.ReceiveCenterService;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class ReceiveCenterServiceImpl implements ReceiveCenterService {

    public static final Logger logger = LoggerFactory.getLogger(ReceiveCenterServiceImpl.class);


    @Autowired
    private CouponProviderApi couponProviderApi;

    @Autowired
    CouponCodeApi couponCodeApi;

    @Autowired
    CouponReceiveApi couponReceiveApi;

    @Autowired
    GoodDetailService goodDetailService;

    @Autowired
    private CouponApi couponApi;

    @Autowired
    private XybbcLock xybbcLock;

    /**
     * @author lll
     * @version V1.0
     * @Description: ?????????????????????
     * @Param: receiveCouponDto
     * @return: Boolean                                                                                                                                                                                                                                                                 <                                                                                                                                                                                                                                                               GoodsCategoryVo>>
     * @date 2019/11/12 13:49
     */
    @GlobalTransactional
    @Override
    public Result receiveCodeCoupon(String fcouponCode, Long fuid) {
        //????????????
        if (null == fuid || null == fcouponCode) {
            throw new BizException(MallExceptionCode.PARAM_ERROR);
        }
        //?????????????????????id
        Result<CouponCode> couponCode = couponCodeApi.queryOneByCriteria(Criteria.of(CouponCode.class)
                .andEqualTo(CouponCode::getFcouponCode, fcouponCode)
                .fields(CouponCode::getFcouponId, CouponCode::getFisUsed));
        if (!couponCode.isSuccess()) {
            logger.error("?????????????????????id?????????fcouponCode{}", fcouponCode);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null == couponCode.getData()) {
            throw new BizException(MallExceptionCode.CODE_NOT_COUPON);
        }
        if (couponCode.getData().getFisUsed() == 1) {
            throw new BizException(MallExceptionCode.CODE_IS_USED);
        }
        //???????????????--?????????????????????--????????????????????????--????????????--????????????--?????????????????????--??????????????????
        Result<Coupon> couponResult = couponApi.queryOneByCriteria(Criteria.of(Coupon.class)
                .andEqualTo(Coupon::getFcouponId, couponCode.getData().getFcouponId())
                .andEqualTo(Coupon::getFcouponStatus, CouponStatusEnum.PUSHED.getCode())
                .andEqualTo(Coupon::getFreleaseType, CouponReleaseTypeEnum.COUPON_CODE_ACTIVATION.getCode())
                .fields(Coupon::getFperLimit, Coupon::getFsurplusReleaseQty, Coupon::getFvalidityType,
                        Coupon::getFvalidityEnd, Coupon::getFreleaseTimeEnd, Coupon::getFreleaseTimeStart,
                        Coupon::getFreleaseTimeType, Coupon::getFcouponId
                ));
        if (!couponResult.isSuccess()) {
            logger.error("????????????????????????fcouponId{}", couponCode.getData().getFcouponId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        Coupon coupon = couponResult.getData();
        //????????????????????????
        if (null == coupon) {
            throw new BizException(MallExceptionCode.COUPON_IS_NOT_EXIST);
        }
        //??????????????????
        if (coupon.getFsurplusReleaseQty() <= 0) {
            throw new BizException(MallExceptionCode.COUPON_IS_PAID_OUT);
        }
        Date now = new Date();
        //???????????????????????????
        if (coupon.getFvalidityType() == 1 && now.after(coupon.getFvalidityEnd())) {
            return Result.failure(MallExceptionCode.COUPON_IS_INVALID);
        }
        //????????????????????????
        if (coupon.getFreleaseTimeType() == 2 && (now.after(coupon.getFreleaseTimeEnd()) || now.before(coupon.getFreleaseTimeStart()))) {
            throw new BizException(MallExceptionCode.COUPON_IS_NOT_TIME);
        }
        //????????????????????????????????????
        Result<Integer> countResult = couponReceiveApi.countByCriteria(Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFuid, fuid)
                .andEqualTo(CouponReceive::getFcouponId, couponResult.getData().getFcouponId()));
        if (!couponResult.isSuccess()) {
            logger.error("?????????????????????????????????????????????fcouponId{} fuid{}", couponCode.getData().getFcouponId(), fuid);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        //????????????????????????
        if (null != countResult.getData()) {
            if (countResult.getData().equals(coupon.getFperLimit())) {
                throw new BizException(MallExceptionCode.COUPON_IS_MAX);
            }
        }
        //?????????????????????????????????????????????
        CouponQueryDto couponQueryDto = new CouponQueryDto();
        couponQueryDto.setUserId(fuid);
        List<Integer> list = new ArrayList<>();
        list.add(8);
        couponQueryDto.setReleaseTypes(list);
        //???????????????????????????
        Result<List<CouponQueryVo>> couponQueryVoResult = couponProviderApi.queryByUserId(couponQueryDto);
        if (!couponQueryVoResult.isSuccess()) {
            logger.error("????????????????????????????????????????????????{}", JSONObject.toJSONString(couponQueryDto));
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (CollectionUtils.isEmpty(couponQueryVoResult.getData())) {
            throw new BizException(MallExceptionCode.USER_NOT_COUPON);
        }
        //???????????????????????????????????????
        List<Long> couponIdList = couponQueryVoResult.getData().stream().map(s -> s.getFcouponId()).collect(Collectors.toList());
        if (!couponIdList.contains(couponCode.getData().getFcouponId())) {
            throw new BizException(MallExceptionCode.USER_NOT_RIGHT_COUPON);
        }
        ReceiveCouponDto receiveCouponDto = new ReceiveCouponDto();
        receiveCouponDto.setFuid(fuid);
        receiveCouponDto.setFcouponCode(fcouponCode);
        //??????????????????????????????
        Result result = this.receiveCoupon(receiveCouponDto);
        return result;
    }


    /**
     * @author lll
     * @version V1.0
     * @Description: ?????????????????????????????????
     * @Param: receiveCouponDto
     * @return: Result                                                                                                                                                                                                                                                                 <                                                                                                                                                                                                                                                               GoodsCategoryVo>>
     * @date 2019/11/12 13:49
     */
    public Result receiveCoupon(ReceiveCouponDto receiveCouponDto) {
        Long fuid = receiveCouponDto.getFuid();
        String fcouponCode = receiveCouponDto.getFcouponCode();
        try {
            //????????????
            CouponReleaseDto couponReleaseDto = new CouponReleaseDto();
            couponReleaseDto.setCouponScene(CouponScene.COUPON_CODE_ACTIVATION);
            couponReleaseDto.setUserId(fuid);
            couponReleaseDto.setCouponCode(fcouponCode);
            couponReleaseDto.setDeltaValue(-1);
            //??????????????????
            Result receiveReceive = couponProviderApi.receive(couponReleaseDto);
            if (!receiveReceive.isSuccess()) {
                logger.error("???????????????????????????????????????{}", JSONObject.toJSONString(couponReleaseDto));
                return receiveReceive;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.success(true);
    }


    /**
     * @author lll
     * @version V1.0
     * @Description: ???????????????????????????
     * @Param: receiveCouponDto
     * @return: List<CouponCenterVo>                                                                                                                                                                                                                                                                 <                                                                                                                                                                                                                                                               GoodsCategoryVo>>
     * @date 2019/11/12 13:49
     */
    @Override
    public Result<List<ReceiveCenterCouponVo>> getCoupon(QueryCouponDto queryCouponDto) {
        //????????????id
        if (null == queryCouponDto.getUserId()) {
            throw new BizException(MallExceptionCode.PARAM_ERROR);
        }
        List<Integer> list = new ArrayList<>();
        CouponQueryDto couponQueryDto = new CouponQueryDto();
        //?????????????????????2????????????????????????
        list.add(2);
        couponQueryDto.setReleaseTypes(list);
        couponQueryDto.setUserId(queryCouponDto.getUserId());
        Result<List<CouponQueryVo>> couponQueryVos = couponProviderApi.queryByUserId(couponQueryDto);
        List<ReceiveCenterCouponVo> receiveCenterCouponVoList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(couponQueryVos.getData())) {
            //???????????????
            Collections.sort(couponQueryVos.getData(), new Comparator<CouponQueryVo>() {
                @Override
                public int compare(CouponQueryVo o1, CouponQueryVo o2) {
                    return o2.getFmodifyTime().compareTo(o1.getFmodifyTime());
                }
            });
            for (CouponQueryVo couponQueryVo : couponQueryVos.getData()) {
                //??????????????????????????????
                Result<Integer> countResult = couponReceiveApi.countByCriteria(Criteria.of(CouponReceive.class)
                        .fields(CouponReceive::getFcouponId)
                        .andEqualTo(CouponReceive::getFuid, couponQueryDto.getUserId())
                        .andEqualTo(CouponReceive::getFcouponId, couponQueryVo.getFcouponId()));
                if (!countResult.isSuccess()) {
                    logger.error("???????????????????????????????????????userid{} couponId{}", couponQueryDto.getUserId(), couponQueryVo.getFcouponId());
                    throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                }
                //???????????????????????????????????????
                if (countResult.getData() < couponQueryVo.getFperLimit()) {
                    //??????????????????
                    ReceiveCenterCouponVo receiveCenterCouponVo = new ReceiveCenterCouponVo();
                    receiveCenterCouponVo.setFcouponId(couponQueryVo.getFcouponId());
                    receiveCenterCouponVo.setFcouponName(couponQueryVo.getFcouponName());
                    receiveCenterCouponVo.setFcouponType(couponQueryVo.getFcouponType());
                    receiveCenterCouponVo.setFvalidityEnd(couponQueryVo.getFvalidityEnd());
                    receiveCenterCouponVo.setFvalidityStart(couponQueryVo.getFvalidityStart());
                    receiveCenterCouponVo.setNowDate(new Date());
                    receiveCenterCouponVo.setReceiveNum(Long.valueOf(countResult.getData()));
                    receiveCenterCouponVo.setFperLimit(couponQueryVo.getFperLimit());
                    receiveCenterCouponVo.setFvalidityType(couponQueryVo.getFvalidityType());
                    receiveCenterCouponVo.setFvalidityDays(couponQueryVo.getFvalidityDays());
                    receiveCenterCouponVo.setFthresholdAmount(PriceUtil.toYuan(couponQueryVo.getFthresholdAmount()));
                    //??????????????????1?????????????????????100???2?????????????????????10
                    if (couponQueryVo.getFcouponType().equals(CouponTypeEnum.FULL_REDUCTION.getCode())) {
                        receiveCenterCouponVo.setFdeductionValue(PriceUtil.toYuan(couponQueryVo.getFdeductionValue()));
                    } else {
                        receiveCenterCouponVo.setFdeductionValue(new BigDecimal(couponQueryVo.getFdeductionValue()).divide(new BigDecimal("100"), 2,BigDecimal.ROUND_DOWN));
                    }
                    receiveCenterCouponVoList.add(receiveCenterCouponVo);
                }
            }
        }
        return Result.success(receiveCenterCouponVoList);
    }


    /**
     * @author lll
     * @version V1.0
     * @Description: ???????????????????????????
     * @Param: receiveCouponDto
     * @return: Result                                                                                                                                                                                                                                                                 <                                                                                                                                                                                                                                                               GoodsCategoryVo>>
     * @date 2019/11/12 13:49
     */
    @Override
    @GlobalTransactional
    public Result addReceiveCoupon(Long fcouponId, Long fuid) {
        //????????????
        if (null == fuid || null == fcouponId) {
            return Result.failure(MallExceptionCode.PARAM_ERROR);
        }
        //???????????????--?????????????????????--????????????????????????--????????????--????????????--?????????????????????--??????????????????
        Result<Coupon> couponResult = couponApi.queryOneByCriteria(Criteria.of(Coupon.class)
                .andEqualTo(Coupon::getFcouponId, fcouponId)
                .andEqualTo(Coupon::getFcouponStatus, CouponStatusEnum.PUSHED.getCode())
                .andEqualTo(Coupon::getFreleaseType, CouponReleaseTypeEnum.PAGE_RECEIVE.getCode())
                .fields(Coupon::getFperLimit, Coupon::getFsurplusReleaseQty, Coupon::getFvalidityType,
                        Coupon::getFvalidityEnd, Coupon::getFreleaseTimeEnd, Coupon::getFreleaseTimeStart,
                        Coupon::getFreleaseTimeType
                ));
        if (!couponResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        Coupon coupon = couponResult.getData();
        //????????????????????????
        if (null == coupon) {
            throw new BizException(MallExceptionCode.COUPON_IS_NOT_EXIST);
        }
        //??????????????????
        if (coupon.getFsurplusReleaseQty() <= 0) {
            throw new BizException(MallExceptionCode.COUPON_IS_PAID_OUT);
        }
        Date now = new Date();
        //???????????????????????????
        if (coupon.getFvalidityType() == 1 && now.after(coupon.getFvalidityEnd())) {
            return Result.failure(MallExceptionCode.COUPON_IS_INVALID);
        }
        //????????????????????????
        if (coupon.getFreleaseTimeType() == 2 && (now.after(coupon.getFreleaseTimeEnd()) || now.before(coupon.getFreleaseTimeStart()))) {
            throw new BizException(MallExceptionCode.COUPON_IS_NOT_TIME);
        }
        //??????????????????????????????
        Result<Integer> countResult = couponReceiveApi.countByCriteria(Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFuid, fuid)
                .andEqualTo(CouponReceive::getFcouponId, fcouponId));
        if (!couponResult.isSuccess()) {
            logger.error("???????????????????????????????????????userid{} couponId{}", fuid, fcouponId);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        //????????????????????????
        if (null != countResult.getData()) {
            if (countResult.getData().equals(coupon.getFperLimit())) {
                throw new BizException(MallExceptionCode.COUPON_IS_MAX);
            }
        }
        ReceiveCouponDto receiveCouponDto = new ReceiveCouponDto();
        receiveCouponDto.setFuid(fuid);
        receiveCouponDto.setFcouponId(fcouponId);
        Result result = this.receiveCenterCoupon(receiveCouponDto);
        return result;
    }


    /**
     * @author lll
     * @version V1.0
     * @Description: ?????????????????????????????????
     * @Param: receiveCouponDto
     * @return: Result                                                                                                                                                                                                                                                                 <                                                                                                                                                                                                                                                               GoodsCategoryVo>>
     * @date 2019/11/12 13:49
     */
    public Result receiveCenterCoupon(ReceiveCouponDto receiveCouponDto) {
        Long fcouponId = receiveCouponDto.getFcouponId();
        Long fuid = receiveCouponDto.getFuid();
        //???????????????
        String lockKey = StringUtils.join(Lists.newArrayList(MallConstants.MALL_RECEIVE_COUPON, fcouponId, fuid), ":");
        String lockValue = RandomUtils.getUUID();
        try {
            Ensure.that(xybbcLock.tryLockTimes(lockKey, lockValue, 3, 6)).isTrue(MallExceptionCode.SYSTEM_BUSY_ERROR);
            //???????????????????????????
            CouponReleaseDto couponReleaseDto = new CouponReleaseDto();
            couponReleaseDto.setCouponScene(CouponScene.PAGE_RECEIVE);
            couponReleaseDto.setCouponId(fcouponId);
            couponReleaseDto.setUserId(fuid);
            couponReleaseDto.setAlreadyReceived(true);
            couponReleaseDto.setDeltaValue(-1);
            Result updateReleaseResult = couponProviderApi.updateReleaseQty(couponReleaseDto);
            Ensure.that(updateReleaseResult.isSuccess()).isTrue(new MallExceptionCode(updateReleaseResult.getCode(), updateReleaseResult.getMsg()));
            //??????????????????
            Result receiveReceive = couponProviderApi.receive(couponReleaseDto);
            Ensure.that(receiveReceive.isSuccess()).isTrue(new MallExceptionCode(receiveReceive.getCode(), receiveReceive.getMsg()));
            return receiveReceive;
        } catch (Exception e) {
            e.printStackTrace();
            BizException be = (BizException) e;
            return Result.failure(be.getStatus());
        } finally {
            xybbcLock.releaseLock(lockKey, lockValue);
        }

    }


}
