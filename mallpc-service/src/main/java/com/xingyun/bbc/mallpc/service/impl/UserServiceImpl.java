package com.xingyun.bbc.mallpc.service.impl;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.xingyun.bbc.activity.api.CouponProviderApi;
import com.xingyun.bbc.activity.enums.CouponScene;
import com.xingyun.bbc.activity.model.dto.CouponReleaseDto;
import com.xingyun.bbc.common.jwt.XyUserJwtManager;
import com.xingyun.bbc.common.redis.XyRedisManager;
import com.xingyun.bbc.core.enums.ResultStatus;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.helper.api.SMSApi;
import com.xingyun.bbc.core.market.api.CouponApi;
import com.xingyun.bbc.core.market.api.CouponReceiveApi;
import com.xingyun.bbc.core.market.enums.CouponReceiveStatusEnum;
import com.xingyun.bbc.core.market.enums.CouponReleaseTypeEnum;
import com.xingyun.bbc.core.market.enums.CouponStatusEnum;
import com.xingyun.bbc.core.market.enums.CouponTypeEnum;
import com.xingyun.bbc.core.market.po.Coupon;
import com.xingyun.bbc.core.market.po.CouponReceive;
import com.xingyun.bbc.core.operate.api.GuidePageApi;
import com.xingyun.bbc.core.operate.api.MarketUserApi;
import com.xingyun.bbc.core.operate.api.MarketUserStatisticsApi;
import com.xingyun.bbc.core.operate.enums.GuideConfigType;
import com.xingyun.bbc.core.operate.enums.GuidePageType;
import com.xingyun.bbc.core.operate.po.GuidePage;
import com.xingyun.bbc.core.operate.po.MarketUser;
import com.xingyun.bbc.core.operate.po.MarketUserStatistics;
import com.xingyun.bbc.core.query.Criteria;
import com.xingyun.bbc.core.user.api.UserAccountApi;
import com.xingyun.bbc.core.user.api.UserApi;
import com.xingyun.bbc.core.user.api.UserLoginInformationApi;
import com.xingyun.bbc.core.user.po.User;
import com.xingyun.bbc.core.user.po.UserAccount;
import com.xingyun.bbc.core.user.po.UserLoginInformation;
import com.xingyun.bbc.core.utils.DateUtil;
import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.core.utils.StringUtil;
import com.xingyun.bbc.mallpc.common.components.DozerHolder;
import com.xingyun.bbc.mallpc.common.components.RedisHolder;
import com.xingyun.bbc.mallpc.common.components.lock.XybbcLock;
import com.xingyun.bbc.mallpc.common.constants.MallPcRedisConstant;
import com.xingyun.bbc.mallpc.common.constants.UserConstants;
import com.xingyun.bbc.mallpc.common.ensure.Ensure;
import com.xingyun.bbc.mallpc.common.exception.MallPcExceptionCode;
import com.xingyun.bbc.mallpc.common.utils.*;
import com.xingyun.bbc.mallpc.infrastructure.interceptor.register.RegisterMessage;
import com.xingyun.bbc.mallpc.model.dto.PageDto;
import com.xingyun.bbc.mallpc.model.dto.user.ResetPasswordDto;
import com.xingyun.bbc.mallpc.model.dto.user.SendSmsCodeDto;
import com.xingyun.bbc.mallpc.model.dto.user.UserLoginDto;
import com.xingyun.bbc.mallpc.model.dto.user.UserRegisterDto;
import com.xingyun.bbc.mallpc.model.vo.PageVo;
import com.xingyun.bbc.mallpc.model.vo.TokenInfoVo;
import com.xingyun.bbc.mallpc.model.vo.coupon.CouponVo;
import com.xingyun.bbc.mallpc.model.vo.coupon.MyCouponVo;
import com.xingyun.bbc.mallpc.model.vo.user.SendSmsCodeVo;
import com.xingyun.bbc.mallpc.model.vo.user.UserLoginVo;
import com.xingyun.bbc.mallpc.service.UserService;
import com.xingyun.bbc.message.business.WaitSendInfo;
import eu.bitwalker.useragentutils.UserAgent;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

import static com.xingyun.bbc.mallpc.common.constants.MallPcRedisConstant.USER_COUNT;
import static com.xingyun.bbc.mallpc.common.constants.MallPcRedisConstant.USER_COUNT_LOCK;
import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toList;

/**
 * @author nick
 * @version 1.0.0
 * @date 2019-11-19
 * @copyright ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Resource
    private DozerHolder convertor;

    @Resource
    private XyUserJwtManager xyUserJwtManager;

    @Resource
    private XyRedisManager redisManager;

    @Resource
    private UserApi userApi;

    @Resource
    private SMSApi smsApi;

    @Resource
    private CouponApi couponApi;

    @Resource
    private UserAccountApi userAccountApi;

    @Resource
    private CouponProviderApi couponProviderApi;

    @Resource
    private GuidePageApi guidePageApi;

    @Resource
    private MarketUserApi marketUserApi;

    @Resource
    private MarketUserStatisticsApi marketUserStatisticsApi;

    @Resource
    private CouponReceiveApi couponReceiveApi;

    @Resource
    private PageUtils pageUtils;

    @Resource
    private RedisHolder redisHolder;

    @Resource
    private XybbcLock xybbcLock;

    @Resource
    private UserLoginInformationApi userLoginInformationApi;

    @Resource
    private RegisterMessage registerMessage;

    /**
     * @author nick
     * @date 2019-11-19
     * @description :  ????????????
     * @version 1.0.0
     */
    @Override
    public Result<UserLoginVo> userLogin(UserLoginDto userLoginDto) {
        //??????
        String passWord = EncryptUtils.aesDecrypt(userLoginDto.getPassword());
        passWord = MD5Util.toMd5(passWord);
        Result<User> userResult = userApi.queryOneByCriteria(Criteria.of(User.class)
                .andEqualTo(User::getFisDelete, "0")
                .andEqualTo(User::getFpasswd, passWord)
                .andLeft().orEqualTo(User::getFmobile, userLoginDto.getUserAccount())
                .orEqualTo(User::getFuname, userLoginDto.getUserAccount()).addRight());
        Ensure.that(userResult).isNotNullData(MallPcExceptionCode.LOGIN_FAILED);
        Ensure.that(userResult.getData().getFfreezeStatus()).isEqual(1, MallPcExceptionCode.ACCOUNT_FREEZE);
        UserLoginVo userLoginVo = createToken(userResult.getData());
        //????????????????????????
        User user = new User();
        user.setFuid(userLoginVo.getFuid());
        user.setFlastloginTime(new Date());
        userApi.updateNotNull(user);
        //????????????????????????
        updateUserLoginInformation(userLoginVo.getFuid());
        return Result.success(userLoginVo);
    }

    private void updateUserLoginInformation(Long fuid) {
        UserLoginInformation information = new UserLoginInformation();
        information.setFuid(fuid);
        information.setFloginMethod("PC???");
        information.setFloginSite("");
        information.setFipAdress(HttpUtil.getClientIP(RequestHolder.getRequest()));
        String ua = RequestHolder.getRequest().getHeader("user-agent");
        UserAgent userAgent = UserAgent.parseUserAgentString(ua);
        information.setFunitType(userAgent.getBrowser().getName());
        information.setFoperatingSystem(userAgent.getOperatingSystem().getName());
        information.setFuniqueIdentificationCode("");
        information.setFphysicalAddress("");
        userLoginInformationApi.create(information);
    }

    private UserLoginVo createToken(User user) {
        UserLoginVo userLoginVo = convertor.convert(user, UserLoginVo.class);
        long expire = UserConstants.Token.TOKEN_AUTO_LOGIN_EXPIRATION;
        TokenInfoVo tokenInfoVo = new TokenInfoVo();
        tokenInfoVo.setFverifyStatus(user.getFverifyStatus()).setFoperateType(user.getFoperateType());
        String token = xyUserJwtManager.createJwt(user.getFuid().toString(), JSON.toJSONString(tokenInfoVo), expire);
        userLoginVo.setExpire(expire);
        userLoginVo.setToken(token);
        if (StringUtils.isBlank(userLoginVo.getFnickname())) {
            userLoginVo.setFunameIsModify(1);
        } else {
            userLoginVo.setFunameIsModify(0);

        }
        if (StringUtils.isBlank(user.getFwithdrawPasswd())) {
            userLoginVo.setFwithdrawPasswdStatus(0);
        } else {
            userLoginVo.setFwithdrawPasswdStatus(1);
        }
        //??????????????????????????? ????????????+30
        Date createTime = user.getFcreateTime();
        Date verifyEndTime = DateUtil.addDays(createTime, 30);
        userLoginVo.setFreeVerifyEndTime(verifyEndTime);
        //????????????
        Days days = Days.daysBetween(DateTime.now(), new DateTime(verifyEndTime));
        int remainDays = days.getDays() < 0 ? 0 : days.getDays() + 1;
        userLoginVo.setFreeVerifyRemainDays(remainDays + "???");
        return userLoginVo;
    }

    /**
     * @author nick
     * @date 2019-11-19
     * @description :  ??????
     * @version 1.0.0
     */
    @Override
    @GlobalTransactional
    public Result<UserLoginVo> register(UserRegisterDto userRegisterDto) {
        MarketUser marketUser = null;
        String fmobile = userRegisterDto.getFmobile();
        // ????????????????????????
        Ensure.that(checkVerifyCode(fmobile, userRegisterDto.getVerifyCode())).isTrue(MallPcExceptionCode.SMS_AUTH_NUM_ERROR);
        // ???????????????????????????
        Ensure.that(Objects.isNull(findUserByMobile(fmobile))).isTrue(MallPcExceptionCode.REGISTER_MOBILE_EXIST);
        String passWord = EncryptUtils.aesDecrypt(userRegisterDto.getPassword());
        Ensure.that(StringUtils.isNotBlank(passWord)).isTrue(MallPcExceptionCode.PASSWORD_CAN_NOT_BE_NULL);
        // ??????????????????
        Ensure.that(passWord.length()).isGt(5, MallPcExceptionCode.PASSWORD_ILLEGAL).isLt(33, MallPcExceptionCode.PASSWORD_ILLEGAL);
        // ???????????????
        User user = new User();
        if (StringUtils.isNotBlank(userRegisterDto.getFinviter())) {
            Result<MarketUser> marketUserResult = marketUserApi.queryOneByCriteria(Criteria.of(MarketUser.class)
                    .fields(MarketUser::getFuid, MarketUser::getFextensionCode,MarketUser::getFmarketUserId)
                    .andEqualTo(MarketUser::getFextensionCode, userRegisterDto.getFinviter()));
            Ensure.that(marketUserResult.isSuccess()).isTrue(MallPcExceptionCode.SYSTEM_ERROR);
            marketUser = marketUserResult.getData();
            Ensure.that(Objects.nonNull(marketUser)).isTrue(MallPcExceptionCode.EXTENSION_CODE_NOT_EXIST);
            user.setFinviter(marketUser.getFextensionCode());
            user.setFmarketBdId(marketUser.getFmarketUserId());
        }
        passWord = MD5Util.toMd5(passWord);
        user.setFregisterFrom("web");
        user.setFmobile(fmobile);
        user.setFuname(fmobile);
        user.setFpasswd(passWord);
        user.setFfreezeStatus(1);
        user.setFverifyStatus(1);
        Date date = new Date();
        user.setFlastloginTime(date);
        user.setFmobileValidTime(date);
        Result<User> userResult = userApi.saveAndReturn(user);
        Ensure.that(userResult).isNotNull(MallPcExceptionCode.SYSTEM_ERROR);
        Long fuid = userResult.getData().getFuid();
        Result<User> result = userApi.queryOneByCriteria(Criteria.of(User.class).andEqualTo(User::getFuid, fuid));
        Ensure.that(result).isNotNull(MallPcExceptionCode.SYSTEM_ERROR);
        UserAccount userAccount = new UserAccount();
        userAccount.setFuid(fuid);
        Ensure.that(userAccountApi.create(userAccount)).isSuccess(MallPcExceptionCode.SYSTEM_ERROR);
        UserLoginVo userLoginVo = createToken(result.getData());
        // ??????????????????????????????????????????????????????
        if (Objects.nonNull(marketUser)) {
            // ???????????????
            MarketUserStatistics marketUserStatistics = new MarketUserStatistics();
            marketUserStatistics.setFextensionCode(marketUser.getFextensionCode());
            marketUserStatistics.setFuid(marketUser.getFuid());
            marketUserStatistics.setFinvitorUid(user.getFuid());
            Ensure.that(marketUserStatisticsApi.create(marketUserStatistics).isSuccess()).isTrue(MallPcExceptionCode.SYSTEM_ERROR);
        }
        // ?????????????????????????????????
        receiveCoupon(fuid);
        // ????????????????????????
        incrUserCount();
        WaitSendInfo waitSendInfo = new WaitSendInfo();
        waitSendInfo.setTargetId(fuid);
        waitSendInfo.setBusinessId(fmobile);
        registerMessage.onApplicationEvent(waitSendInfo);
        return Result.success(userLoginVo);
    }

    /**
     * ????????????????????????
     */
    @Async
    void incrUserCount() {
        if (redisHolder.exists(USER_COUNT)) {
            redisHolder.incr(USER_COUNT);
        } else {
            xybbcLock.tryLock(USER_COUNT_LOCK, () -> {
                if (redisHolder.exists(USER_COUNT)) {
                    redisHolder.incr(USER_COUNT);
                    return;
                }
                User user = new User();
                Result<Integer> result = userApi.count(user);
                if (result.getData() != null && result.getData() > 0) {
                    redisHolder.setnx(USER_COUNT, result.getData());
                    redisHolder.incr(USER_COUNT);
                }
            });
        }
    }

    private boolean checkVerifyCode(String fmobile, String verifyCode) {
        String key = StringUtils.join(MallPcRedisConstant.VERIFY_CODE_PREFIX, fmobile);
        if (Objects.isNull(redisManager.get(key))) {
            return false;
        }
        if (Objects.equals(String.valueOf(redisManager.get(key)), verifyCode)) {
            return true;
        }
        return false;
    }

    /**
     * @author nick
     * @date 2019-11-19
     * @description :  ???????????????
     * @version 1.0.0
     */
    @Override
    public Result<SendSmsCodeVo> sendSmsCode(SendSmsCodeDto sendSmsCodeDto) {
        SendSmsCodeVo sendSmsCodeVo = new SendSmsCodeVo();
        Integer sourceType = sendSmsCodeDto.getSourceType();
        String fmobile = sendSmsCodeDto.getFmobile();
        Ensure.that(StringUtilExtention.mobileCheck(fmobile)).isTrue(MallPcExceptionCode.BIND_MOBILE_ERROR);
        User user = findUserByMobile(fmobile);
        switch (sourceType) {
            case 0:
                // ??????
                Ensure.that(Objects.isNull(user)).isTrue(MallPcExceptionCode.REGISTER_MOBILE_EXIST);
                break;
            default:
                // ????????????
                Ensure.that(Objects.nonNull(user)).isTrue(MallPcExceptionCode.ACCOUNT_NOT_EXIST);
                break;
        }
        // ??????????????????
        Ensure.that(Objects.isNull(redisManager.get(fmobile))).isTrue(MallPcExceptionCode.SMS_AUTH_IS_SEND);
        // ???????????????ip????????????
        String ipAddress = HttpUtil.getClientIP(RequestHolder.getRequest());
        String limit_key = StringUtils.join(MallPcRedisConstant.VERIFY_CODE_LIMIT_PREFIX, ipAddress);
        if (Objects.nonNull(redisManager.get(limit_key))) {
            Integer count = (Integer) redisManager.get(limit_key);
            Ensure.that(count).isLt(UserConstants.Sms.MAX_IP_SMS_SEND_TIME, MallPcExceptionCode.USER_SEND_SMS_FAILD);
            if (count > UserConstants.Sms.CAPTCHA_THRESHOLD) {
                // ??????????????????
                sendSmsCodeVo.setIsCheck(1);
            }
        }
        // ???????????????
        sendSms(ipAddress, fmobile);
        return Result.success(sendSmsCodeVo);
    }

    private void sendSms(String ipAddress, String fmobile) {
        // ???????????????
        String verifyCode = generateAuthNum(4);
        String content = StringUtils.join("?????????????????????", verifyCode, "????????????????????????????????????????????????????????????", "?????????????????????");
        smsApi.sendSms(fmobile, content);
        // ????????????redis
        redisManager.set(StringUtils.join(MallPcRedisConstant.VERIFY_CODE_PREFIX, fmobile), verifyCode, UserConstants.Sms.MOBILE_AUTH_CODE_EXPIRE_TIME);
        // ?????????????????????
        redisManager.set(fmobile, MallPcRedisConstant.DEFAULT_LOCK_VALUE, UserConstants.Sms.MOBILE_SEND_SMS_TIME);
        // ??????ip????????????
        String limit_key = StringUtils.join(MallPcRedisConstant.VERIFY_CODE_LIMIT_PREFIX, ipAddress);
        redisManager.incr(limit_key);
        //???????????????????????????
        long secondsLeftToday = 86400 - DateUtils.getFragmentInSeconds(Calendar.getInstance(), Calendar.DATE);
        redisManager.expire(limit_key, secondsLeftToday);
    }

    public User findUserByMobile(String mobile) {
        Result<User> userResult = userApi.queryOneByCriteria(Criteria.of(User.class)
                .andEqualTo(User::getFisDelete, "0")
                .andEqualTo(User::getFmobile, mobile));
        Ensure.that(userResult.isSuccess()).isTrue(MallPcExceptionCode.SYSTEM_ERROR);
        return userResult.getData();
    }


    /**
     * @author nick
     * @date 2019-11-19
     * @description :  ???????????????
     * @version 1.0.0
     */
    private void receiveCoupon(Long fuid) {
        Result<List<Coupon>> couponResult = couponApi.queryByCriteria(Criteria.of(Coupon.class)
                .fields(Coupon::getFcouponId)
                .andEqualTo(Coupon::getFcouponStatus, CouponStatusEnum.PUSHED.getCode())
                .andEqualTo(Coupon::getFreleaseType, CouponReleaseTypeEnum.REGISTER.getCode())
                .andGreaterThan(Coupon::getFsurplusReleaseQty, 0));
        Ensure.that(couponResult.isSuccess()).isTrue(MallPcExceptionCode.SYSTEM_ERROR);
        log.info("?????????????????????,??????{}",couponResult.getData().size());
        couponResult.getData().stream().forEach(coupon -> couponProviderApi.receive(new CouponReleaseDto()
                .setCouponId(coupon.getFcouponId())
                .setCouponScene(CouponScene.REGISTER)
                .setUserId(fuid)));
    }

    /**
     * @author nick
     * @date 2019-11-19
     * @description :  ???????????????????????????
     * @version 1.0.0
     */
    @Override
    public Result<MyCouponVo> queryRegisterCoupon() {
        Integer fuserCouponStatus = CouponReceiveStatusEnum.NOT_USED.getCode();
        Long userId = RequestHolder.getUserId();
        //????????????????????????????????????
        Criteria<CouponReceive, Object> criteria = Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFuid, userId)
                .andEqualTo(CouponReceive::getFuserCouponStatus, fuserCouponStatus);

        Result<Integer> countResult = couponReceiveApi.countByCriteria(criteria);
        if (!countResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        criteria.fields(CouponReceive::getFcouponId, CouponReceive::getFvalidityStart,
                CouponReceive::getFvalidityEnd, CouponReceive::getFuserCouponStatus)
                .sortDesc(CouponReceive::getFmodifyTime);
        Result<List<CouponReceive>> listResult = couponReceiveApi.queryByCriteria(criteria);
        if (!listResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        Integer count = countResult.getData();
        PageVo<CouponVo> couponPageVo = pageUtils.convert(count, listResult.getData(), CouponVo.class, new PageDto());
        //?????????????????????
        for (CouponVo couponVo : couponPageVo.getList()) {
            Result<Coupon> couponResult = couponApi.queryOneByCriteria(Criteria.of(Coupon.class)
                    .andEqualTo(Coupon::getFcouponId, couponVo.getFcouponId())
                    .fields(Coupon::getFcouponName, Coupon::getFcouponType,
                            Coupon::getFthresholdAmount, Coupon::getFdeductionValue,
                            Coupon::getFvalidityType, Coupon::getFvalidityDays, Coupon::getFreleaseType, Coupon::getFapplicableSku));
            if (!couponResult.isSuccess()) {
                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
            }
            Coupon coupon = couponResult.getData();
            couponVo.setFcouponName(coupon.getFcouponName());
            couponVo.setFcouponType(coupon.getFcouponType());
            couponVo.setFthresholdAmount(PriceUtil.toYuan(coupon.getFthresholdAmount()));
            couponVo.setFapplicableSku(coupon.getFapplicableSku());
            //??????????????????1????????????2?????????
            if (coupon.getFcouponType().equals(CouponTypeEnum.FULL_REDUCTION.getCode())) {
                couponVo.setFdeductionValue(PriceUtil.toYuan(coupon.getFdeductionValue()));
            } else {
                couponVo.setFdeductionValue(new BigDecimal(coupon.getFdeductionValue()).divide(new BigDecimal("10"), 1, BigDecimal.ROUND_HALF_UP));
            }
            couponVo.setFvalidityType(coupon.getFvalidityType());
            couponVo.setFvalidityDays(coupon.getFvalidityDays());
            couponVo.setFreleaseType(coupon.getFreleaseType());
        }
        // ??????
        List<CouponVo> collect = couponPageVo.getList().stream()
                .sorted(comparing(CouponVo::getFvalidityEnd).thenComparing(CouponVo::getFdeductionValue, reverseOrder()))
                .collect(toList());
        couponPageVo.setList(collect);
        //???????????????????????????
        MyCouponVo myCouponVo = new MyCouponVo();
        myCouponVo.setCouponVo(couponPageVo);
        myCouponVo.setUnUsedNum(fuserCouponStatus.equals(CouponReceiveStatusEnum.NOT_USED.getCode()) ? count
                : this.getCouponByStatus(userId, CouponReceiveStatusEnum.NOT_USED.getCode()));
        myCouponVo.setUsedNum(fuserCouponStatus.equals(CouponReceiveStatusEnum.USED.getCode()) ? count
                : this.getCouponByStatus(userId, CouponReceiveStatusEnum.USED.getCode()));
        myCouponVo.setExpiredNum(fuserCouponStatus.equals(CouponReceiveStatusEnum.NULLIFY.getCode()) ? count
                : this.getCouponByStatus(userId, CouponReceiveStatusEnum.NULLIFY.getCode()));
        myCouponVo.setNowDate(new Date());

        return Result.success(myCouponVo);
    }

    private Integer getCouponByStatus(Long userId, Integer fuserCouponStatus) {
        Criteria<CouponReceive, Object> criteriaStatus = Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFuid, userId)
                .andEqualTo(CouponReceive::getFuserCouponStatus, fuserCouponStatus);
        Result<Integer> countResult = couponReceiveApi.countByCriteria(criteriaStatus);
        if (!countResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        return countResult.getData();
    }

    /**
     * @author nick
     * @date 2019-11-19
     * @description :  ????????????
     * @version 1.0.0
     */
    @Override
    public Result resetPwd(ResetPasswordDto resetPasswordDto) {
        String fmobile = resetPasswordDto.getFmobile();
        // ?????????????????????
        Ensure.that(StringUtilExtention.mobileCheck(fmobile)).isTrue(MallPcExceptionCode.BIND_MOBILE_ERROR);
        String newPassword = EncryptUtils.aesDecrypt(resetPasswordDto.getNewPassword());
        // String newPassword = resetPasswordDto.getNewPassword();
        // ?????????????????????
        Ensure.that(newPassword.length()).isGt(5, MallPcExceptionCode.PASSWORD_ILLEGAL).isLt(33, MallPcExceptionCode.PASSWORD_ILLEGAL);
        newPassword = MD5Util.toMd5(newPassword);
        String verifyCode = resetPasswordDto.getVerifyCode();
        // ???????????????
        Ensure.that(checkVerifyCode(fmobile, verifyCode)).isTrue(MallPcExceptionCode.SMS_AUTH_NUM_ERROR);
        // ????????????????????????
        Result<User> userResult = userApi.queryOneByCriteria(Criteria.of(User.class)
                .fields(User::getFuid, User::getFpasswd).andEqualTo(User::getFmobile, fmobile));
        Ensure.that(userResult).isNotNull(MallPcExceptionCode.BIND_MOBILE_ERROR);
        // ????????????????????????
        User user = userResult.getData();
        String fpasswd = user.getFpasswd();
        Ensure.that(Objects.equals(newPassword, fpasswd)).isFalse(MallPcExceptionCode.PASSWORD_NOT_CHANGE);
        // ????????????
        user.setFpasswd(newPassword);
        Ensure.that(userApi.updateNotNull(user).isSuccess()).isTrue(MallPcExceptionCode.SYSTEM_ERROR);
        return Result.success();
    }

    /**
     * @author nick
     * @date 2019-11-19
     * @description :  ??????/??????????????????
     * @version 1.0.0
     */
    @Override
    public Result modifiyPayPwd(ResetPasswordDto resetPasswordDto) {
        String fmobile = resetPasswordDto.getFmobile();
        // ?????????????????????
        Ensure.that(StringUtilExtention.mobileCheck(fmobile)).isTrue(MallPcExceptionCode.BIND_MOBILE_ERROR);
        String newPassword = EncryptUtils.aesDecrypt(resetPasswordDto.getNewPassword());
        // ?????????????????????
        Ensure.that(newPassword.length()).isGt(5, MallPcExceptionCode.PASSWORD_ILLEGAL).isLt(33, MallPcExceptionCode.PASSWORD_ILLEGAL);
        newPassword = MD5Util.toMd5(newPassword);
        String verifyCode = resetPasswordDto.getVerifyCode();
        // ???????????????
        Ensure.that(checkVerifyCode(fmobile, verifyCode)).isTrue(MallPcExceptionCode.SMS_AUTH_NUM_ERROR);
        // ????????????????????????
        Result<User> userResult = userApi.queryOneByCriteria(Criteria.of(User.class)
                .fields(User::getFuid, User::getFpasswd).andEqualTo(User::getFmobile, fmobile));
        Ensure.that(userResult).isNotNull(MallPcExceptionCode.BIND_MOBILE_ERROR);
        // ????????????????????????
        User user = userResult.getData();
        String fwithdrawPasswd = user.getFwithdrawPasswd();
        Ensure.that(Objects.equals(newPassword, fwithdrawPasswd)).isFalse(MallPcExceptionCode.PASSWORD_NOT_CHANGE);
        // ????????????
        user.setFwithdrawPasswd(newPassword);
        Ensure.that(userApi.updateNotNull(user).isSuccess()).isTrue(MallPcExceptionCode.SYSTEM_ERROR);
        return Result.success();
    }

    @Override
    public Result<String> guideLogin() {
        Result<GuidePage> guidePageResult = guidePageApi.queryOneByCriteria(Criteria.of(GuidePage.class)
                .andEqualTo(GuidePage::getFguideType, GuideConfigType.PC_CONFIG.getCode())
                .andEqualTo(GuidePage::getFisDelete, 0)
                .andEqualTo(GuidePage::getFtype, GuidePageType.LOGIN_PAGE.getCode())
                .fields(GuidePage::getFimgUrl, GuidePage::getFguideId)
        );
        Ensure.that(guidePageResult.isSuccess()).isTrue(MallPcExceptionCode.SYSTEM_ERROR);
        return Result.success(Objects.nonNull(guidePageResult.getData()) && StringUtil.isNotBlank(guidePageResult.getData().getFimgUrl()) ? guidePageResult.getData().getFimgUrl() : "");
    }

    /**
     * @author nick
     * @date 2019-11-22
     * @description :  ??????????????????
     * @version 1.0.0
     */
    @Override
    public Result<UserLoginVo> queryLoginInfo() {
        Long userId = RequestHolder.getUserId();
        Result<User> userResult = userApi.queryOneByCriteria(Criteria.of(User.class)
                .andEqualTo(User::getFisDelete, "0")
                .andEqualTo(User::getFuid, userId));
        Ensure.that(userResult).isNotNullData(MallPcExceptionCode.ACCOUNT_NOT_EXIST);
        Ensure.that(userResult.getData().getFfreezeStatus()).isEqual(1, MallPcExceptionCode.ACCOUNT_FREEZE);
        return Result.success(createToken(userResult.getData()));
    }

    private String generateAuthNum(int authLen) {
        StringBuilder str = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < authLen; i++) {
            str.append(random.nextInt(10));
        }
        return str.toString();
    }
}
