package com.xingyun.bbc.mallpc.service.impl;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.xingyun.bbc.activity.api.CouponProviderApi;
import com.xingyun.bbc.activity.enums.CouponScene;
import com.xingyun.bbc.activity.model.dto.CouponQueryDto;
import com.xingyun.bbc.activity.model.dto.CouponReleaseDto;
import com.xingyun.bbc.activity.model.vo.CouponQueryVo;
import com.xingyun.bbc.core.enums.ResultStatus;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.market.api.*;
import com.xingyun.bbc.core.market.enums.*;
import com.xingyun.bbc.core.market.po.*;
import com.xingyun.bbc.core.operate.api.CityRegionApi;
import com.xingyun.bbc.core.operate.api.CountryApi;
import com.xingyun.bbc.core.operate.po.Country;
import com.xingyun.bbc.core.order.api.RegularListApi;
import com.xingyun.bbc.core.order.po.RegularList;
import com.xingyun.bbc.core.query.Criteria;
import com.xingyun.bbc.core.sku.api.*;
import com.xingyun.bbc.core.sku.enums.GoodsSkuEnums;
import com.xingyun.bbc.core.sku.enums.SkuBatchEnums;
import com.xingyun.bbc.core.sku.po.*;
import com.xingyun.bbc.core.supplier.api.SupplierSkuBatchApi;
import com.xingyun.bbc.core.supplier.api.SupplierWarehouseApi;
import com.xingyun.bbc.core.supplier.enums.TradeTypeEnums;
import com.xingyun.bbc.core.supplier.po.SupplierSkuBatch;
import com.xingyun.bbc.core.supplier.po.SupplierWarehouse;
import com.xingyun.bbc.core.user.api.UserApi;
import com.xingyun.bbc.core.user.api.UserDeliveryApi;
import com.xingyun.bbc.core.user.api.UserVerifyApi;
import com.xingyun.bbc.core.user.enums.UserVerifyStatusEnum;
import com.xingyun.bbc.core.user.po.User;
import com.xingyun.bbc.core.user.po.UserDelivery;
import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.mallpc.common.components.DozerHolder;
import com.xingyun.bbc.mallpc.common.components.lock.XybbcLock;
import com.xingyun.bbc.mallpc.common.constants.MallPcConstants;
import com.xingyun.bbc.mallpc.common.ensure.Ensure;
import com.xingyun.bbc.mallpc.common.exception.MallPcExceptionCode;
import com.xingyun.bbc.mallpc.common.utils.PriceUtil;
import com.xingyun.bbc.mallpc.common.utils.RandomUtils;
import com.xingyun.bbc.mallpc.common.utils.ResultUtils;
import com.xingyun.bbc.mallpc.model.dto.detail.GoodsDetailMallDto;
import com.xingyun.bbc.mallpc.model.dto.detail.ReceiveCouponDto;
import com.xingyun.bbc.mallpc.model.dto.detail.SkuDiscountTaxDto;
import com.xingyun.bbc.mallpc.model.vo.detail.*;
import com.xingyun.bbc.mallpc.service.GoodDetailService;
import com.xingyun.bbc.mallpc.service.GoodsService;
import com.xingyun.bbc.order.api.FreightApi;
import com.xingyun.bbc.order.model.dto.freight.FreightDto;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dozer.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Service
public class GoodDetailServiceImpl implements GoodDetailService {

    public static final Logger logger = LoggerFactory.getLogger(GoodDetailService.class);

    @Resource
    private SupplierWarehouseApi warehouseApi;

    @Resource
    private SupplierSkuBatchApi supplierSkuBatchApi;

    @Resource
    private UserApi userApi;

    @Resource
    private UserVerifyApi userVerifyApi;

    @Resource
    private CityRegionApi cityRegionApi;

    @Resource
    private GoodsApi goodsApi;

    @Resource
    private GoodsBrandApi goodsBrandApi;

    @Resource
    private GoodsCategoryApi goodsCategoryApi;

    @Resource
    private GoodsTradeInfoApi goodsTradeInfoApi;

    @Resource
    private CountryApi countryApi;

    @Resource
    private GoodsThumbImageApi goodsThumbImageApi;

    @Resource
    private GoodsSkuApi goodsSkuApi;

    @Resource
    private GoodsAttributeApi goodsAttributeApi;

    @Resource
    private SkuBatchApi skuBatchApi;

    @Resource
    private SkuBatchPackageApi skuBatchPackageApi;

    @Resource
    private GoodsSkuBatchPriceApi goodsSkuBatchPriceApi;

    @Resource
    private SkuBatchUserPriceApi skuBatchUserPriceApi;

    @Resource
    private SkuUserDiscountConfigApi skuUserDiscountConfigApi;

    @Resource
    private UserDeliveryApi userDeliveryApi;

    @Resource
    private FreightApi freightApi;

    @Resource
    private RegularListApi regularListApi;

    @Resource
    private CouponApi couponApi;

    @Resource
    private CouponReceiveApi couponReceiveApi;

    @Resource
    private CouponBindUserApi couponBindUserApi;

    @Resource
    private CouponProviderApi couponProviderApi;

    @Resource
    private CouponApplicableSkuApi couponApplicableSkuApi;

    @Resource
    private CouponApplicableSkuConditionApi couponApplicableSkuConditionApi;

    @Resource
    private CouponReleaseApi couponReleaseApi;

    @Resource
    private CouponReleaseConditionApi couponReleaseConditionApi;

    @Resource
    private GoodsService goodsService;

    @Resource
    private Mapper dozerMapper;

    @Resource
    private DozerHolder dozerHolder;

    @Resource
    private XybbcLock xybbcLock;

    @Override
    public Result<List<String>> getGoodDetailPic(Long fgoodsId, Long fskuId) {
        List<String> result = new ArrayList<>();
        //??????sku??????
        if (null != fskuId) {
            Result<GoodsSku> skuPic = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                    .andEqualTo(GoodsSku::getFskuId, fskuId)
                    .fields(GoodsSku::getFskuThumbImage));
            if (null != skuPic.getData()) {
                if (StringUtils.isNotBlank(skuPic.getData().getFskuThumbImage())) {
                    result.add(skuPic.getData().getFskuThumbImage());
                }
            }
        }
        //??????spu??????
        Result<List<GoodsThumbImage>> spuLis = goodsThumbImageApi.queryByCriteria(Criteria.of(GoodsThumbImage.class)
                .andEqualTo(GoodsThumbImage::getFgoodsId, fgoodsId)
                .fields(GoodsThumbImage::getFimgUrl));
        List<String> spuPic = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(spuLis.getData())) {
            spuPic = spuLis.getData().stream().filter(goodsThumbImage ->
                    StringUtils.isNotBlank(goodsThumbImage.getFimgUrl())).map(goodsThumbImage ->
                    goodsThumbImage.getFimgUrl()).collect(toList());
        }
        result.addAll(spuPic);
        return Result.success(result);
    }

    /**
     * @see GoodDetailService#getGoodSkuPic(Long)
     * @param fskuId
     * @return
     */
    @Override
    public Result<String> getGoodSkuPic(Long fskuId) {
        Result<GoodsSku> skuPic = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFskuId, fskuId)
                .fields(GoodsSku::getFskuThumbImage));
        if (skuPic.getData() == null) {
            throw new BizException(new MallPcExceptionCode("", "sku ?????????"));
        }
        return Result.success(skuPic.getData().getFskuThumbImage());
    }

    @Override
    public Result<GoodsVo> getGoodDetailBasic(Long fgoodsId, Long fskuId) {
        //????????????spu????????????
        Result<Goods> goodsBasic = goodsApi.queryById(fgoodsId);
        if (!goodsBasic.isSuccess()) {
            logger.info("??????spu id {}????????????????????????????????????????????????", fgoodsId);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null == goodsBasic.getData()) {
            return Result.success();
        }
        GoodsVo goodsVo = dozerMapper.map(goodsBasic.getData(), GoodsVo.class);

        //????????????????????????
        String tradeType = TradeTypeEnums.getTradeType(goodsVo.getFtradeId().toString());
        if (null == tradeType) {
            logger.info("??????spu id {}????????????????????????????????????", fgoodsId);
            throw new BizException(ResultStatus.NOT_IMPLEMENTED);
        }
        goodsVo.setFtradeType(tradeType);

        //?????????????????????sku???????????????????????????
        Result<List<GoodsSku>> goodsSkuResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFgoodsId, fgoodsId)
                .fields(GoodsSku::getFskuDesc, GoodsSku::getFskuThumbImage, GoodsSku::getFskuName, GoodsSku::getFskuId));
        Ensure.that(goodsSkuResult.isSuccess()).isTrue(new MallPcExceptionCode(goodsSkuResult.getCode(), goodsSkuResult.getMsg()));

        List<GoodsSku> goodsSkuList = goodsSkuResult.getData();
        List<GoodsAlterVo> goodsSkuAlterVo = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(goodsSkuList)) {
            for (GoodsSku goodsSku : goodsSkuList) {
                GoodsAlterVo goodsAlterVo = new GoodsAlterVo();
                //?????????spu???????????????????????????sku?????????
                goodsAlterVo.setFgoodsImgUrl(goodsSku.getFskuThumbImage());
                goodsAlterVo.setFgoodsName(goodsSku.getFskuName());
                goodsAlterVo.setFskuDesc(goodsSku.getFskuDesc());
                goodsAlterVo.setFskuId(goodsSku.getFskuId());
                goodsSkuAlterVo.add(goodsAlterVo);
            }
            goodsVo.setGoodsSkuAlterVo(goodsSkuAlterVo);
        }

        //?????????????????????????????????????????????icon
        goodsVo.setFbrandName("");
        goodsVo.setFbrandCountryName("");
        goodsVo.setFcountryIcon("");
        if (null != goodsVo.getFbrandId()) {
            GoodsBrand goodsBrand = goodsBrandApi.queryOneByCriteria(Criteria.of(GoodsBrand.class)
                    .andEqualTo(GoodsBrand::getFbrandId, goodsVo.getFbrandId())
                    .fields(GoodsBrand::getFbrandName, GoodsBrand::getFbrandLogo, GoodsBrand::getFcountryName)).getData();
            if (null != goodsBrand) {
                goodsVo.setFbrandName(goodsBrand.getFbrandName());
                goodsVo.setFbrandCountryName(goodsBrand.getFcountryName());
                goodsVo.setFbrandLogo(goodsBrand.getFbrandLogo() == null ? "" : goodsBrand.getFbrandLogo());
                Country country = countryApi.queryOneByCriteria(Criteria.of(Country.class)
                        .andEqualTo(Country::getFcountryName, goodsBrand.getFcountryName())
                        .fields(Country::getFcountryIcon)).getData();
                if (null != country) {
                    goodsVo.setFcountryIcon(country.getFcountryIcon());
                }
            }
        }

//        //???????????????????????????
//        goodsVo.setFgoodsOrigin("");
//        if (null != goodsVo.getForiginId()) {
//            Country country = countryApi.queryOneByCriteria(Criteria.of(Country.class)
//                    .andEqualTo(Country::getFcountryId, goodsVo.getForiginId())
//                    .fields(Country::getFcountryName)).getData();
//            if (null != country) {
//                goodsVo.setFgoodsOrigin(country.getFcountryName());
//            }
//        }
        return Result.success(goodsVo);
    }


    @Override
    public Result<GoodspecificationVo> getGoodsSpecifi(Long fgoodsId) {
        //??????????????????
        GoodspecificationVo result = new GoodspecificationVo();

        Result<List<GoodsSku>> goodsSkuResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFgoodsId, fgoodsId)
                .andEqualTo(GoodsSku::getFskuStatus, GoodsSkuEnums.Status.OnShelves.getValue())
                .andEqualTo(GoodsSku::getFisDelete, "0")
                .fields(GoodsSku::getFskuId, GoodsSku::getFskuCode, GoodsSku::getFskuSpecValue));
        Ensure.that(goodsSkuResult.isSuccess()).isTrue(new MallPcExceptionCode(goodsSkuResult.getCode(), goodsSkuResult.getMsg()));
        if (CollectionUtils.isEmpty(goodsSkuResult.getData())) {
            return Result.success();
        }
        //sku??????
        List<GoodsSkuVo> skuRes = dozerHolder.convert(goodsSkuResult.getData(), GoodsSkuVo.class);
        List<Long> skuIds = skuRes.stream().map(GoodsSkuVo::getFskuId).collect(toList());
        Result<List<SkuBatch>> skuBatchResult = skuBatchApi.queryByCriteria(Criteria.of(SkuBatch.class)
                .andIn(SkuBatch::getFskuId, skuIds)
                .andEqualTo(SkuBatch::getFbatchStatus, SkuBatchEnums.Status.OnShelves.getValue())
                .andEqualTo(SkuBatch::getFbatchPutwaySort, 1)//????????????????????????1???
                .fields(SkuBatch::getFqualityStartDate, SkuBatch::getFqualityEndDate, SkuBatch::getFsupplierSkuBatchId, SkuBatch::getFskuId));

        List<SkuBatch> skuBatchList = ResultUtils.getListNotEmpty(skuBatchResult, MallPcExceptionCode.SKU_BATCH_IS_NONE);
        Map<Long, List<SkuBatch>> skuPatchMap = skuBatchList.stream().collect(Collectors.groupingBy(SkuBatch::getFskuId));
        //????????????
        List<GoodsSkuBatchVo> batchRes = new ArrayList<>();
        //????????????
        List<GoodsSkuBatchPackageVo> packageRes = new ArrayList<>();
        //????????????????????????
        List<GoodspecificationDetailVo> detailRes = new ArrayList<>();

        for (GoodsSkuVo skuVo : skuRes) {
            List<GoodsSkuBatchVo> batchVert = dozerHolder.convert(skuPatchMap.get(skuVo.getFskuId()), GoodsSkuBatchVo.class);
            List<String> batchIds = batchVert.stream().map(GoodsSkuBatchVo::getFsupplierSkuBatchId).collect(toList());
            if (CollectionUtils.isEmpty(batchIds)) {
                GoodspecificationDetailVo detailVo = new GoodspecificationDetailVo();
                detailVo.setFskuCode(skuVo.getFskuCode());
                detailVo.setFskuId(skuVo.getFskuId());
                detailRes.add(detailVo);
                continue;
            }
            Result<List<SkuBatchPackage>> skuBatchPackageResult = skuBatchPackageApi.queryByCriteria(Criteria.of(SkuBatchPackage.class)
                    .andIn(SkuBatchPackage::getFsupplierSkuBatchId, batchIds)
                    .fields(SkuBatchPackage::getFbatchPackageId, SkuBatchPackage::getFbatchPackageNum,
                            SkuBatchPackage::getFbatchStartNum, SkuBatchPackage::getFsupplierSkuBatchId));
            List<SkuBatchPackage> packageList = ResultUtils.getListNotEmpty(skuBatchPackageResult, MallPcExceptionCode.SKU_PACKAGE_IS_NONE);
            logger.info("??????????????????=======" + JSON.toJSONString(skuBatchPackageResult.getData()));
            Map<String, List<SkuBatchPackage>> packageMap = packageList.stream().collect(Collectors.groupingBy(SkuBatchPackage::getFsupplierSkuBatchId));
            batchRes.addAll(batchVert);
            for (GoodsSkuBatchVo batchVo : batchVert) {
                List<GoodsSkuBatchPackageVo> packageVert = dozerHolder.convert(packageMap.get(batchVo.getFsupplierSkuBatchId()), GoodsSkuBatchPackageVo.class);
                packageRes.addAll(packageVert);
                for (GoodsSkuBatchPackageVo packageVo : packageVert) {
                    GoodspecificationDetailVo detailVo = new GoodspecificationDetailVo();
                    detailVo.setFskuId(skuVo.getFskuId());
                    detailVo.setFskuCode(skuVo.getFskuCode());
                    detailVo.setFskuSpecValue(skuVo.getFskuSpecValue());
                    detailVo.setFskuBatchId(batchVo.getFsupplierSkuBatchId());
                    detailVo.setFqualityEndDate(this.fillFqualityDateStr(batchVo.getFqualityStartDate(), batchVo.getFqualityEndDate()));
                    detailVo.setFbatchPackageId(packageVo.getFbatchPackageId());
                    detailVo.setFbatchPackageNum(packageVo.getFbatchPackageNum());
                    detailVo.setFbatchStartNum(packageVo.getFbatchStartNum());
                    detailRes.add(detailVo);
                }
            }
        }

        skuRes.stream().filter(distinctByKey(b -> b.getFskuId()));
        batchRes.stream().filter(distinctByKey(b -> b.getFsupplierSkuBatchId()));
        packageRes.stream().filter(distinctByKey(b -> b.getFbatchPackageId()));

        //???????????????????????????????????? ?????????????????????????????????????????? ^_^???#
        List<MallTVo> skuMall = new ArrayList<>();
        List<MallTVo> batchMall = new ArrayList<>();
        List<MallTVo> packageMall = new ArrayList<>();
        for (GoodsSkuVo skuRe : skuRes) {
            MallTVo tVoSku = new MallTVo();
            tVoSku.setTId(skuRe.getFskuId());
            tVoSku.setTName(skuRe.getFskuSpecValue());
            skuMall.add(tVoSku);
        }
        GoodspecificationExVo skuEx = new GoodspecificationExVo();
        skuEx.setIdType(1);
        skuEx.setKeyType("goodsSkuVoLis");
        skuEx.setTitle("??????");
        skuEx.setItem(skuMall);

        for (GoodsSkuBatchVo batchRe : batchRes) {
            MallTVo tVoBatch = new MallTVo();
            tVoBatch.setTId(batchRe.getFsupplierSkuBatchId());
            tVoBatch.setTName(this.fillFqualityDateStr(batchRe.getFqualityStartDate(), batchRe.getFqualityEndDate()));
            batchMall.add(tVoBatch);
        }
        GoodspecificationExVo batchEx = new GoodspecificationExVo();
        batchEx.setIdType(2);
        batchEx.setKeyType("goodsSkuBatchVoLis");
        batchEx.setTitle("??????");
        batchEx.setItem(batchMall);

        for (GoodsSkuBatchPackageVo packageRe : packageRes) {
            MallTVo tVoPackage = new MallTVo();
            tVoPackage.setTId(packageRe.getFbatchPackageId());
            tVoPackage.setTName(packageRe.getFbatchPackageNum().toString());
            packageMall.add(tVoPackage);
        }
        GoodspecificationExVo packageEx = new GoodspecificationExVo();
        packageEx.setIdType(1);
        packageEx.setKeyType("goodsSkuBatchPackageVoLis");
        packageEx.setTitle("??????");
        packageEx.setItem(packageMall);

        List<GoodspecificationExVo> items = new ArrayList<>();
        items.add(skuEx);
        items.add(batchEx);
        items.add(packageEx);

        result.setItems(items);
        result.setDetailLis(detailRes);
        return Result.success(result);
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    //?????????????????????
    private String fillFqualityDateStr(Date fqualityStartDate, Date fqualityEndDate) {
        if (Objects.nonNull(fqualityStartDate) && Objects.nonNull(fqualityEndDate)) {
            Calendar startCal = Calendar.getInstance();
            startCal.setTime(fqualityStartDate);
            Calendar endCal = Calendar.getInstance();
            endCal.setTime(fqualityEndDate);
            StringBuffer sbf = new StringBuffer();
            //2020/03/16 ???????????? ?????????????????????????????????,?????????????????????
            if (isSameYearAndMonth(startCal, endCal)) {
                sbf.append(String.valueOf(startCal.get(Calendar.YEAR)).substring(2, 4)).append("???")
                        .append(startCal.get(Calendar.MONTH) + 1).append("???");
            } else {
                sbf.append(String.valueOf(startCal.get(Calendar.YEAR)).substring(2, 4)).append("???")
                        .append(startCal.get(Calendar.MONTH) + 1).append("???").append("~")
                        .append(String.valueOf(endCal.get(Calendar.YEAR)).substring(2, 4)).append("???")
                        .append(endCal.get(Calendar.MONTH) + 1).append("???");
            }
            return sbf.toString();
        }
        return "";
    }

    public Boolean isSameYearAndMonth(Calendar cal1, Calendar cal2) {
        if (Objects.nonNull(cal1) && Objects.nonNull(cal2)) {
            boolean isSameYear = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);
            boolean isSameMonth = cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH);
            return isSameYear && isSameMonth;
        }
        return false;
    }

    @Override
    public Result<Map<String, List<GoodsAttributeVo>>> getGoodsAttribute(Long fgoodsId) {
        //??????????????????
        Result<List<GoodsAttribute>> goodsAttributeRes = goodsAttributeApi.queryByCriteria(Criteria.of(GoodsAttribute.class)
                .andEqualTo(GoodsAttribute::getFgoodsId, fgoodsId)
                .fields(GoodsAttribute::getFclassAttributeItemVal, GoodsAttribute::getFclassAttributeId, GoodsAttribute::getFclassAttributeName));
        Ensure.that(goodsAttributeRes.isSuccess()).isTrue(new MallPcExceptionCode(goodsAttributeRes.getCode(), goodsAttributeRes.getMsg()));
        List<GoodsAttributeVo> convert = dozerHolder.convert(goodsAttributeRes.getData(), GoodsAttributeVo.class);

        Map<String, List<GoodsAttributeVo>> collect = convert.stream().collect(Collectors.groupingBy(GoodsAttributeVo::getFclassAttributeName, toList()));
        return Result.success(collect);
    }

    @Override
    public Result<GoodsPriceVo> getGoodPrice(GoodsDetailMallDto goodsDetailMallDto) {
        //token????????????????????????--??????????????????
        if (!UserVerifyStatusEnum.AUTHENTICATED.getCode().equals(goodsDetailMallDto.getFverifyStatus())) {
            //????????????????????????
            Result<User> userResult = userApi.queryOneByCriteria(Criteria.of(User.class)
                    .andEqualTo(User::getFuid, goodsDetailMallDto.getFuid())
                    .fields(User::getFoperateType, User::getFverifyStatus));
            if (!userResult.isSuccess() || null == userResult.getData()) {
                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
            }
            //?????????????????????????????????????????????
            goodsDetailMallDto.setFoperateType(userResult.getData().getFoperateType());
            goodsDetailMallDto.setFverifyStatus(userResult.getData().getFverifyStatus());
        }

        //??????????????????
        GoodsPriceVo priceResult = new GoodsPriceVo();
        //?????????
        if (null != goodsDetailMallDto.getFbatchPackageId()) {
            GoodsDetailMallDto param = dozerMapper.map(goodsDetailMallDto, GoodsDetailMallDto.class);
            BigDecimal packagePrice = this.getPackagePrice(goodsDetailMallDto, param);
            priceResult.setRealPrice(PriceUtil.toYuan(packagePrice));
            priceResult.setPriceStart(PriceUtil.toYuan(packagePrice));
            Result<SkuBatchPackage> skuBatchPackageResult = skuBatchPackageApi.queryById(goodsDetailMallDto.getFbatchPackageId());
            if (!skuBatchPackageResult.isSuccess()) {
                throw new BizException(MallPcExceptionCode.SYSTEM_ERROR);
            }
            String skuBatchId = skuBatchPackageResult.getData().getFsupplierSkuBatchId();
            SupplierSkuBatch skuBatch = getSkuBatchById(skuBatchId);
            priceResult.setFgoodsPackType(skuBatch.getFgoodsPackType());
            Long warehouseId = skuBatch.getFsupplierWarehouseId();
            SupplierWarehouse warehouse = getSupplierWarehouseById(warehouseId);
            priceResult.setFwarehouseName(warehouse.getFsupplierWarehouseName());

        }
        //?????????
        if (StringUtils.isNotBlank(goodsDetailMallDto.getFsupplierSkuBatchId()) && null == goodsDetailMallDto.getFbatchPackageId()) {
            GoodsDetailMallDto param = dozerMapper.map(goodsDetailMallDto, GoodsDetailMallDto.class);
            priceResult = this.getBatchPrice(goodsDetailMallDto, param);
            this.dealGoodDetailPriceToYuan(priceResult);
            SupplierSkuBatch skuBatch = getSkuBatchById(goodsDetailMallDto.getFsupplierSkuBatchId());
            priceResult.setFgoodsPackType(skuBatch.getFgoodsPackType());
            Long warehouseId = skuBatch.getFsupplierWarehouseId();
            SupplierWarehouse warehouse = getSupplierWarehouseById(warehouseId);
            priceResult.setFwarehouseName(warehouse.getFsupplierWarehouseName());

        }
        //???sku
        if (null != goodsDetailMallDto.getFskuId() && StringUtils.isBlank(goodsDetailMallDto.getFsupplierSkuBatchId()) && null == goodsDetailMallDto.getFbatchPackageId()) {
            GoodsDetailMallDto param = dozerMapper.map(goodsDetailMallDto, GoodsDetailMallDto.class);
            priceResult = this.getSkuPrice(goodsDetailMallDto, param);
            this.dealGoodDetailPriceToYuan(priceResult);
        }
        //???spu
        if (null != goodsDetailMallDto.getFgoodsId() && null == goodsDetailMallDto.getFskuId() && StringUtils.isBlank(goodsDetailMallDto.getFsupplierSkuBatchId()) && null == goodsDetailMallDto.getFbatchPackageId()) {
            GoodsDetailMallDto param = dozerMapper.map(goodsDetailMallDto, GoodsDetailMallDto.class);
            priceResult = this.getSpuPrice(goodsDetailMallDto, param);
            this.dealGoodDetailPriceToYuan(priceResult);
        }
        //??????????????? ?????????????????????PriceStart???????????????????????????????????????
        if (Objects.nonNull(priceResult.getPriceStart()) && Objects.isNull(priceResult.getPriceEnd())) {
            //???????????????????????? 1.???????????? 2.??????????????? 3.??????????????? 4.??????????????????
            Result<SkuBatch> skuBatchResult = skuBatchApi.queryOneByCriteria(Criteria.of(SkuBatch.class)
                    .andEqualTo(SkuBatch::getFsupplierSkuBatchId, goodsDetailMallDto.getFsupplierSkuBatchId())
                    .fields(SkuBatch::getFbatchPriceType, SkuBatch::getFfreightId, SkuBatch::getFsupplierSkuBatchId));
            Ensure.that(skuBatchResult.isSuccess()).isTrue(new MallPcExceptionCode(skuBatchResult.getCode(), skuBatchResult.getMsg()));
            SkuBatch fskuBatch = skuBatchResult.getData();
            if (fskuBatch == null) {
                throw new BizException(new MallPcExceptionCode("", "sku????????????????????????id???" + goodsDetailMallDto.getFsupplierSkuBatchId()));
            }
            Integer fbatchPriceType = fskuBatch.getFbatchPriceType();
            //???????????????--????????????--????????????????????????
            BigDecimal freightPrice = BigDecimal.ZERO;
            if (fbatchPriceType.intValue() == 3 || fbatchPriceType.intValue() == 4) {
                // ????????????????????????????????????????????????
                if (null == goodsDetailMallDto.getFdeliveryCityId()) {
                    UserDelivery defautDelivery = userDeliveryApi.queryOneByCriteria(Criteria.of(UserDelivery.class)
                            .andEqualTo(UserDelivery::getFuid, goodsDetailMallDto.getFuid())
                            .andEqualTo(UserDelivery::getFisDefualt, 1)
                            .andEqualTo(UserDelivery::getFisDelete, 0)
                            .fields(UserDelivery::getFuid, UserDelivery::getFdeliveryAddr,
                                    UserDelivery::getFdeliveryProvinceId, UserDelivery::getFdeliveryProvinceName,
                                    UserDelivery::getFdeliveryCityId, UserDelivery::getFdeliveryCityName,
                                    UserDelivery::getFdeliveryAreaId, UserDelivery::getFdeliveryAreaName)).getData();
                    if (null != defautDelivery) {
                        //??????????????????????????????
                        if (null != goodsDetailMallDto.getFnum()) {
                            freightPrice = this.getFreight(goodsDetailMallDto.getFbatchPackageId(), fskuBatch.getFfreightId(),
                                    defautDelivery.getFdeliveryCityId(), fskuBatch.getFsupplierSkuBatchId(), goodsDetailMallDto.getFnum());
                        }
                        priceResult.setFdeliveryAddr(defautDelivery.getFdeliveryAddr() == null ? "" : defautDelivery.getFdeliveryAddr());
                        priceResult.setFdeliveryProvinceName(defautDelivery.getFdeliveryProvinceName() == null ? "" : defautDelivery.getFdeliveryProvinceName());
                        priceResult.setFdeliveryCityName(defautDelivery.getFdeliveryCityName() == null ? "" : defautDelivery.getFdeliveryCityName());
                        priceResult.setFdeliveryAreaName(defautDelivery.getFdeliveryAreaName() == null ? "" : defautDelivery.getFdeliveryAreaName());
                    }
                } else {
                    //??????????????????????????????
                    if (null != goodsDetailMallDto.getFdeliveryCityId() && null != goodsDetailMallDto.getFnum()) {
                        freightPrice = this.getFreight(goodsDetailMallDto.getFbatchPackageId(), fskuBatch.getFfreightId(),
                                goodsDetailMallDto.getFdeliveryCityId(), fskuBatch.getFsupplierSkuBatchId(), goodsDetailMallDto.getFnum());
                    }
                }
            }
            // ??????????????????????????????
            if (freightPrice == null) {
                priceResult.setShowFreightPrice(false);
                freightPrice = BigDecimal.ZERO;
            }
            //????????????????????????---????????????????????????
            //????????????
            Result<GoodsSku> skuTaxResult = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                    .andEqualTo(GoodsSku::getFskuId, goodsDetailMallDto.getFskuId())
                    .fields(GoodsSku::getFskuTaxRate));
            Ensure.that(skuTaxResult.isSuccess()).isTrue(new MallPcExceptionCode(skuTaxResult.getCode(), skuTaxResult.getMsg()));
            Long fskuTaxRate = 0l;
            fskuTaxRate = skuTaxResult.getData().getFskuTaxRate();
            //(?????????*????????????)
            BigDecimal orgPrice = priceResult.getPriceStart().multiply(new BigDecimal(goodsDetailMallDto.getFnum()));
            //?????? = (?????????*???????????? + ??????) * ??????
            BigDecimal taxPrice = BigDecimal.ZERO;

            if (fbatchPriceType.intValue() == 2 || fbatchPriceType.intValue() == 4) {
                taxPrice = orgPrice.add(freightPrice).multiply(new BigDecimal(fskuTaxRate))
                        .divide(MallPcConstants.TEN_THOUSAND, 2, BigDecimal.ROUND_HALF_UP);
            }
            //?????? = (?????????*????????????) + ?????? + ??????
            BigDecimal priceTotal = orgPrice.add(freightPrice).add(taxPrice);
            //???????????? = ?????? / ?????? /??????????????????
            BigDecimal dealUnitPrice = BigDecimal.ZERO;
            if (null != goodsDetailMallDto.getFbatchPackageNum()) {
                dealUnitPrice = priceTotal.divide(new BigDecimal(goodsDetailMallDto.getFnum()).multiply(new BigDecimal(goodsDetailMallDto.getFbatchPackageNum())), 2, BigDecimal.ROUND_HALF_UP);
            }

            priceResult.setPriceStart(priceTotal);
            priceResult.setFreightPrice(freightPrice);
            priceResult.setTaxPrice(taxPrice);
            priceResult.setDealUnitPrice(dealUnitPrice);
        }
        return Result.success(priceResult);
    }

    //?????????????????????
    private BigDecimal getFreight(Long fbatchPackageId, Long ffreightId, Long fdeliveryCityId, String fsupplierSkuBatchId, Long fnum) {
        BigDecimal freightPrice = BigDecimal.ZERO;
        final String exceptionCode = "1012";
        //??????????????????????????????
        Result<SkuBatchPackage> skuBatchPackageResult = skuBatchPackageApi.queryOneByCriteria(Criteria.of(SkuBatchPackage.class)
                .andEqualTo(SkuBatchPackage::getFbatchPackageId, fbatchPackageId)
                .fields(SkuBatchPackage::getFbatchPackageNum));
        if (!skuBatchPackageResult.isSuccess()) {
            logger.info("??????????????????fbatchPackageId {}???????????????????????????", fbatchPackageId);
            throw new BizException(MallPcExceptionCode.BATCH_PACKAGE_NUM_NOT_EXIST);
        }
        Long fbatchPackageNum = skuBatchPackageResult.getData().getFbatchPackageNum();
        FreightDto freightDto = new FreightDto();
        freightDto.setFfreightId(ffreightId);
        freightDto.setFregionId(fdeliveryCityId);
        freightDto.setFbatchId(fsupplierSkuBatchId);
        freightDto.setFbuyNum(fnum * fbatchPackageNum);
        logger.info("????????????--??????????????????{}", JSON.toJSONString(freightDto));
        Result<BigDecimal> freightResult = freightApi.queryFreight(freightDto);
        if (!freightResult.isSuccess()) {
            if (exceptionCode.equals(freightResult.getCode())) {
                return null;
            }
        }
        Ensure.that(freightResult.isSuccess()).isTrue(new MallPcExceptionCode(freightResult.getCode(), freightResult.getMsg()));
        return null == freightResult.getData() ? freightPrice : PriceUtil.toYuan(freightResult.getData());
    }

    private void dealGoodDetailPriceToYuan(GoodsPriceVo goodsPriceVo) {
        if (null != goodsPriceVo.getPriceStart()) {
            goodsPriceVo.setPriceStart(PriceUtil.toYuan(goodsPriceVo.getPriceStart()));
        }
        if (null != goodsPriceVo.getPriceEnd()) {
            goodsPriceVo.setPriceEnd(PriceUtil.toYuan(goodsPriceVo.getPriceEnd()));
        }
        if (null != goodsPriceVo.getTaxStart()) {
            goodsPriceVo.setTaxStart(PriceUtil.toYuan(goodsPriceVo.getTaxStart()));
        }
        if (null != goodsPriceVo.getTaxEnd()) {
            goodsPriceVo.setTaxEnd(PriceUtil.toYuan(goodsPriceVo.getTaxEnd()));
        }
    }

    //????????????????????????
    private BigDecimal getPackagePrice(GoodsDetailMallDto goodsDetailMallDto, GoodsDetailMallDto param) {
        //?????????????????????????????????
        if (param.getFverifyStatus().intValue() == UserVerifyStatusEnum.AUTHENTICATED.getCode()) {
            if (Objects.isNull(param.getFskuDiscount())) {
                SkuDiscountTaxDto isSkuDiscountTax = this.getIsSkuDiscountTax(param.getFskuId());
                param.setFskuTaxRate(isSkuDiscountTax.getFskuTaxRate());
                param.setFskuDiscount(isSkuDiscountTax.getFisUserTypeDiscount());
            }
            //sku??????????????????????????????
            if (param.getFskuDiscount().intValue() == 1) {
                if (Objects.isNull(param.getFskuUserDiscount())) {
                    param.setFskuUserDiscount(this.getIsSkuUserDiscount(goodsDetailMallDto, goodsDetailMallDto.getFoperateType()));
                }
                //sku??????user??????????????????
                if (param.getFskuUserDiscount() == 0) {
                    return this.getGeneralPrice(goodsDetailMallDto, param);
                }
                return this.getDiscountPrice(goodsDetailMallDto, param);
            } else {
                return this.getGeneralPrice(goodsDetailMallDto, param);
            }
        } else {
            return this.getGeneralPrice(goodsDetailMallDto, param);
        }
    }

    //?????????????????????
    private BigDecimal getGeneralPrice(GoodsDetailMallDto goodsDetailMallDto, GoodsDetailMallDto param) {
        Long fbatchPackageId = param.getFbatchPackageId();
        BigDecimal price = BigDecimal.ZERO;
        Result<GoodsSkuBatchPrice> goodsSkuBatchPriceResult = goodsSkuBatchPriceApi.queryOneByCriteria(Criteria.of(GoodsSkuBatchPrice.class)
                .andEqualTo(GoodsSkuBatchPrice::getFbatchPackageId, fbatchPackageId)
                .fields(GoodsSkuBatchPrice::getFbatchSellPrice));
        if (goodsSkuBatchPriceResult.isSuccess() && null != goodsSkuBatchPriceResult.getData()) {
            if (null != goodsSkuBatchPriceResult.getData().getFbatchSellPrice()) {
                price = new BigDecimal(goodsSkuBatchPriceResult.getData().getFbatchSellPrice());
            }
        }
        BigDecimal packageNum = this.getPackageNum(fbatchPackageId);
        //??????????????????????????????--????????????????????????
        return goodsDetailMallDto.getFbatchPackageId() == null ? price.divide(packageNum, 8, BigDecimal.ROUND_HALF_UP) : price;
    }

    //??????????????????
    private BigDecimal getDiscountPrice(GoodsDetailMallDto goodsDetailMallDto, GoodsDetailMallDto param) {
        Integer foperateType = goodsDetailMallDto.getFoperateType();
        Long fbatchPackageId = param.getFbatchPackageId();
        BigDecimal price = BigDecimal.ZERO;
        BigDecimal packageNum = this.getPackageNum(fbatchPackageId);
        Result<SkuBatchUserPrice> skuBatchUserPriceResult = skuBatchUserPriceApi.queryOneByCriteria(Criteria.of(SkuBatchUserPrice.class)
                .andEqualTo(SkuBatchUserPrice::getFbatchPackageId, fbatchPackageId)
                .andEqualTo(SkuBatchUserPrice::getFuserTypeId, foperateType)
                .fields(SkuBatchUserPrice::getFbatchSellPrice));
        if (!skuBatchUserPriceResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null != skuBatchUserPriceResult.getData()) {
            price = new BigDecimal(skuBatchUserPriceResult.getData().getFbatchSellPrice());
        } else {
            return this.getGeneralPrice(goodsDetailMallDto, param);
        }
        //??????????????????????????????--????????????????????????
        return goodsDetailMallDto.getFbatchPackageId() == null ? price.divide(packageNum, 8, BigDecimal.ROUND_HALF_UP) : price;
    }

    //?????????????????????????????? 0 ??? GoodsSkuBatchPrice 1 ??? SkuBatchUserPrice
    private SkuDiscountTaxDto getIsSkuDiscountTax(Long skuId) {
        Result<GoodsSku> goodsSkuResult = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFskuId, skuId)
                .fields(GoodsSku::getFisUserTypeDiscount, GoodsSku::getFskuTaxRate));
        if (!goodsSkuResult.isSuccess() || null == goodsSkuResult.getData()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        SkuDiscountTaxDto skuDiscountTaxDto = new SkuDiscountTaxDto();
        skuDiscountTaxDto.setFisUserTypeDiscount(goodsSkuResult.getData().getFisUserTypeDiscount());
        skuDiscountTaxDto.setFskuTaxRate(new BigDecimal(goodsSkuResult.getData().getFskuTaxRate()));
        return skuDiscountTaxDto;
    }

    //?????????sku???????????????????????????
    private int getIsSkuUserDiscount(GoodsDetailMallDto goodsDetailMallDto, Integer foperateType) {
        Result<Integer> skuUserDiscountResult = skuUserDiscountConfigApi.countByCriteria(Criteria.of(SkuUserDiscountConfig.class)
                .andEqualTo(SkuUserDiscountConfig::getFskuId, goodsDetailMallDto.getFskuId())
                .andEqualTo(SkuUserDiscountConfig::getFuserTypeId, foperateType.longValue())
                .andEqualTo(SkuUserDiscountConfig::getFisDelete, 0)
                .fields(SkuUserDiscountConfig::getFdiscountId));
        if (!skuUserDiscountResult.isSuccess() || null == skuUserDiscountResult.getData()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        goodsDetailMallDto.setFskuUserDiscount(skuUserDiscountResult.getData());
        return skuUserDiscountResult.getData().intValue();
    }

    //?????????????????????
    private BigDecimal getPackageNum(Long fbatchPackageId) {
        Result<SkuBatchPackage> skuBatchPackageResult = skuBatchPackageApi.queryOneByCriteria(Criteria.of(SkuBatchPackage.class)
                .andEqualTo(SkuBatchPackage::getFbatchPackageId, fbatchPackageId)
                .fields(SkuBatchPackage::getFbatchPackageNum));
        if (!skuBatchPackageResult.isSuccess() || null == skuBatchPackageResult.getData()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        return new BigDecimal(skuBatchPackageResult.getData().getFbatchPackageNum());
    }

    //????????????????????????
    private GoodsPriceVo getBatchPrice(GoodsDetailMallDto goodsDetailMallDto, GoodsDetailMallDto param) {
        //?????????
        Result<List<SkuBatchPackage>> batchPackageResult = skuBatchPackageApi.queryByCriteria(Criteria.of(SkuBatchPackage.class)
                .andEqualTo(SkuBatchPackage::getFsupplierSkuBatchId, param.getFsupplierSkuBatchId())
                .fields(SkuBatchPackage::getFbatchPackageId));
        if (!batchPackageResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        GoodsPriceVo priceVo = new GoodsPriceVo();
        BigDecimal zero = BigDecimal.ZERO;
        priceVo.setPriceStart(zero);
        priceVo.setPriceEnd(zero);
        priceVo.setTaxStart(zero);
        priceVo.setTaxEnd(zero);
        //??????sku?????? sku??????????????????
        if (null == param.getFskuTaxRate()) {
            SkuDiscountTaxDto isSkuDiscountTax = this.getIsSkuDiscountTax(param.getFskuId());
            param.setFskuTaxRate(isSkuDiscountTax.getFskuTaxRate());
            param.setFskuDiscount(isSkuDiscountTax.getFisUserTypeDiscount());
            //sku_user????????????
            param.setFskuUserDiscount(this.getIsSkuUserDiscount(param, param.getFoperateType()));
        }
        List<SkuBatchPackage> batchPackage = batchPackageResult.getData();
        if (!CollectionUtils.isEmpty(batchPackage)) {
            for (int i = 0; i < batchPackage.size(); i++) {
                param.setFbatchPackageId(batchPackage.get(i).getFbatchPackageId());
                BigDecimal packagePrice = this.getPackagePrice(goodsDetailMallDto, param);
                if (i == 0) {
                    priceVo.setPriceStart(packagePrice);
                    priceVo.setPriceEnd(packagePrice);
                } else {
                    if (packagePrice.compareTo(priceVo.getPriceStart()) < 0) {
                        priceVo.setPriceStart(packagePrice);
                    }
                    if (packagePrice.compareTo(priceVo.getPriceEnd()) > 0) {
                        priceVo.setPriceEnd(packagePrice);
                    }
                }
            }
        }
        //???????????????????????? 1.???????????? 2.??????????????? 3.??????????????? 4.??????????????????
        Result<SkuBatch> skuBatchResult = skuBatchApi.queryOneByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFsupplierSkuBatchId, param.getFsupplierSkuBatchId())
                .fields(SkuBatch::getFbatchPriceType, SkuBatch::getFfreightId, SkuBatch::getFsupplierSkuBatchId));
        SkuBatch fskuBatch = skuBatchResult.getData();
        Ensure.that(skuBatchResult.isSuccess()).isTrue(new MallPcExceptionCode(skuBatchResult.getCode(), skuBatchResult.getMsg()));
        Integer fbatchPriceType = fskuBatch.getFbatchPriceType();
        BigDecimal skuTaxRate = param.getFskuTaxRate();
        if (fbatchPriceType.intValue() == 1 || fbatchPriceType.intValue() == 3) {
            skuTaxRate = BigDecimal.ZERO;
        }
        skuTaxRate = skuTaxRate.divide(new BigDecimal("10000"), 8, BigDecimal.ROUND_HALF_UP);
        priceVo.setTaxStart(priceVo.getPriceStart().multiply(skuTaxRate));
        priceVo.setTaxEnd(priceVo.getPriceEnd().multiply(skuTaxRate));
        priceVo.setPriceStart(priceVo.getPriceStart().add(priceVo.getTaxStart()));
        priceVo.setPriceEnd(priceVo.getPriceEnd().add(priceVo.getTaxEnd()));
        return priceVo;
    }

    //?????????sku?????????
    private GoodsPriceVo getSkuPrice(GoodsDetailMallDto goodsDetailMallDto, GoodsDetailMallDto param) {
        Result<List<SkuBatch>> skuBatche = skuBatchApi.queryByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFskuId, param.getFskuId())
                .andEqualTo(SkuBatch::getFbatchStatus, SkuBatchEnums.Status.OnShelves.getValue())
                .andEqualTo(SkuBatch::getFbatchPutwaySort, 1)
                .fields(SkuBatch::getFsupplierSkuBatchId));
        if (!skuBatche.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        GoodsPriceVo priceVo = new GoodsPriceVo();
        BigDecimal zero = BigDecimal.ZERO;
        priceVo.setPriceStart(zero);
        priceVo.setPriceEnd(zero);
        priceVo.setTaxStart(zero);
        priceVo.setTaxEnd(zero);
        //sku??????????????????--??????sku??????
        SkuDiscountTaxDto isSkuDiscountTax = this.getIsSkuDiscountTax(param.getFskuId());
        param.setFskuDiscount(isSkuDiscountTax.getFisUserTypeDiscount());
        BigDecimal skuTaxRate = isSkuDiscountTax.getFskuTaxRate();
        param.setFskuTaxRate(skuTaxRate);

        //sku_user????????????
        param.setFskuUserDiscount(this.getIsSkuUserDiscount(param, param.getFoperateType()));

        List<SkuBatch> skuBatcheResult = skuBatche.getData();
        if (!CollectionUtils.isEmpty(skuBatcheResult)) {
            for (int i = 0; i < skuBatcheResult.size(); i++) {
                param.setFsupplierSkuBatchId(skuBatcheResult.get(i).getFsupplierSkuBatchId());
                GoodsPriceVo batchPrice = this.getBatchPrice(goodsDetailMallDto, param);
                if (i == 0) {
                    priceVo.setPriceStart(batchPrice.getPriceStart());
                    priceVo.setPriceEnd(batchPrice.getPriceEnd());
                    priceVo.setTaxStart(batchPrice.getTaxStart());
                    priceVo.setTaxEnd(batchPrice.getTaxEnd());
                } else {
                    if (batchPrice.getPriceStart().compareTo(priceVo.getPriceStart()) < 0) {
                        priceVo.setPriceStart(batchPrice.getPriceStart());
                    }
                    if (batchPrice.getPriceEnd().compareTo(priceVo.getPriceEnd()) > 0) {
                        priceVo.setPriceEnd(batchPrice.getPriceEnd());
                    }
                    if (batchPrice.getTaxStart().compareTo(priceVo.getTaxStart()) < 0) {
                        priceVo.setTaxStart(batchPrice.getTaxStart());
                    }
                    if (batchPrice.getTaxEnd().compareTo(priceVo.getTaxEnd()) > 0) {
                        priceVo.setTaxEnd(batchPrice.getTaxEnd());
                    }
                }
            }
        }
        return priceVo;
    }

    //?????????spu?????????
    private GoodsPriceVo getSpuPrice(GoodsDetailMallDto goodsDetailMallDto, GoodsDetailMallDto param) {
        Result<List<GoodsSku>> listResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFgoodsId, param.getFgoodsId())
                .andEqualTo(GoodsSku::getFskuStatus, GoodsSkuEnums.Status.OnShelves.getValue())
                .andEqualTo(GoodsSku::getFisDelete, "0")
                .fields(GoodsSku::getFskuId));
        if (!listResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        GoodsPriceVo priceVo = new GoodsPriceVo();
        BigDecimal zero = BigDecimal.ZERO;
        priceVo.setPriceStart(zero);
        priceVo.setPriceEnd(zero);
        priceVo.setTaxStart(zero);
        priceVo.setTaxEnd(zero);
        List<GoodsSku> skuResult = listResult.getData();
        if (!CollectionUtils.isEmpty(skuResult)) {
            for (int i = 0; i < skuResult.size(); i++) {
                param.setFskuId(skuResult.get(i).getFskuId());
                GoodsPriceVo skuPrice = this.getSkuPrice(goodsDetailMallDto, param);
                if (i == 0) {
                    priceVo.setPriceStart(skuPrice.getPriceStart());
                    priceVo.setPriceEnd(skuPrice.getPriceEnd());
                    priceVo.setTaxStart(skuPrice.getTaxStart());
                    priceVo.setTaxEnd(skuPrice.getTaxEnd());
                } else {
                    if (skuPrice.getPriceStart().compareTo(priceVo.getPriceStart()) < 0) {
                        priceVo.setPriceStart(skuPrice.getPriceStart());
                    }
                    if (skuPrice.getPriceEnd().compareTo(priceVo.getPriceEnd()) > 0) {
                        priceVo.setPriceEnd(skuPrice.getPriceEnd());
                    }
                    if (skuPrice.getTaxStart().compareTo(priceVo.getTaxStart()) < 0) {
                        priceVo.setTaxStart(skuPrice.getTaxStart());
                    }
                    if (skuPrice.getTaxEnd().compareTo(priceVo.getTaxEnd()) > 0) {
                        priceVo.setTaxEnd(skuPrice.getTaxEnd());
                    }
                }
            }
        }
        return priceVo;
    }

    @Override
    public Result<GoodStockSellVo> getGoodStock(GoodsDetailMallDto goodsDetailMallDto) {
        //????????????--????????????????????????????????????--???????????????????????????
        GoodStockSellVo result = new GoodStockSellVo();
        //?????????
        if (null != goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getBatchStock(goodsDetailMallDto);
        }
        //???sku
        if (null != goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getSkuStock(goodsDetailMallDto);
        }
        //???spu
        if (null != goodsDetailMallDto.getFgoodsId() && null == goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getSpuStock(goodsDetailMallDto);
        }
        return Result.success(result);
    }

    //?????????????????????
    private GoodStockSellVo getBatchStock(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<SkuBatch> batchStockSellResult = skuBatchApi.queryOneByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFsupplierSkuBatchId, goodsDetailMallDto.getFsupplierSkuBatchId())
                .fields(SkuBatch::getFstockRemianNum));
        if (!batchStockSellResult.isSuccess()) {
            logger.info("??????fsupplierSkuBatchId {}???????????????????????????", goodsDetailMallDto.getFsupplierSkuBatchId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        SkuBatch batchStockSell = batchStockSellResult.getData();
        if (null != batchStockSell) {
            result.setFstockRemianNum(batchStockSell.getFstockRemianNum());
        }
        return result;
    }

    //??????sku?????????
    private GoodStockSellVo getSkuStock(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<List<SkuBatch>> skuStockSellResult = skuBatchApi.queryByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFskuId, goodsDetailMallDto.getFskuId())
                .andEqualTo(SkuBatch::getFbatchStatus, SkuBatchEnums.Status.OnShelves.getValue())
                .fields(SkuBatch::getFstockRemianNum));
        if (!skuStockSellResult.isSuccess()) {
            logger.info("??????fskuId {}?????????sku????????????", goodsDetailMallDto.getFskuId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        List<SkuBatch> skuStockSell = skuStockSellResult.getData();
        if (!CollectionUtils.isEmpty(skuStockSell)) {
            long sumSkuStock = skuStockSell.stream().mapToLong(SkuBatch::getFstockRemianNum).sum();
            result.setFstockRemianNum(sumSkuStock);
        }
        return result;
    }

    //??????spu?????????
    private GoodStockSellVo getSpuStock(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<List<SkuBatch>> skuStockSellResult = skuBatchApi.queryByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFgoodsId, goodsDetailMallDto.getFgoodsId())
                .andEqualTo(SkuBatch::getFbatchStatus, SkuBatchEnums.Status.OnShelves.getValue())
                .fields(SkuBatch::getFstockRemianNum));
        if (!skuStockSellResult.isSuccess()) {
            logger.info("??????fgoodsId {}?????????spu????????????", goodsDetailMallDto.getFgoodsId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        List<SkuBatch> skuStockSell = skuStockSellResult.getData();
        if (!CollectionUtils.isEmpty(skuStockSell)) {
            long sumSkuStock = skuStockSell.stream().mapToLong(SkuBatch::getFstockRemianNum).sum();
            result.setFstockRemianNum(sumSkuStock);
        }
        return result;
    }

    @Override
    public Result<GoodStockSellVo> getGoodSell(GoodsDetailMallDto goodsDetailMallDto) {
        //????????????
        GoodStockSellVo result = new GoodStockSellVo();
        //?????????
        if (null != goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getBatchSell(goodsDetailMallDto);
        }
        //???sku
        if (null != goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getSkuSell(goodsDetailMallDto);
        }
        //???spu
        if (null != goodsDetailMallDto.getFgoodsId() && null == goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getSpuSell(goodsDetailMallDto);
        }
        return Result.success(result);
    }

    //?????????????????????
    private GoodStockSellVo getBatchSell(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<SkuBatch> batchStockSellResult = skuBatchApi.queryOneByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFsupplierSkuBatchId, goodsDetailMallDto.getFsupplierSkuBatchId())
                .fields(SkuBatch::getFsellNum));
        if (!batchStockSellResult.isSuccess()) {
            logger.info("??????fsupplierSkuBatchId {}???????????????????????????", goodsDetailMallDto.getFsupplierSkuBatchId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        SkuBatch batchStockSell = batchStockSellResult.getData();
        if (null != batchStockSell) {
            result.setFsellNum(batchStockSell.getFsellNum());
        }
        return result;
    }

    //??????sku?????????
    private GoodStockSellVo getSkuSell(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<GoodsSku> goodsSkuResult = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFskuId, goodsDetailMallDto.getFskuId())
                .fields(GoodsSku::getFsellNum));
        if (!goodsSkuResult.isSuccess()) {
            logger.info("??????fskuId {}?????????sku????????????", goodsDetailMallDto.getFskuId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null != goodsSkuResult.getData()) {
            result.setFsellNum(goodsSkuResult.getData().getFsellNum());
        }
        return result;
    }

    //??????spu?????????
    private GoodStockSellVo getSpuSell(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<List<GoodsSku>> spuStockSellResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFgoodsId, goodsDetailMallDto.getFgoodsId())
                .andEqualTo(GoodsSku::getFskuStatus, GoodsSkuEnums.Status.OnShelves.getValue())
                .andEqualTo(GoodsSku::getFisDelete, "0")
                .fields(GoodsSku::getFsellNum));
        if (!spuStockSellResult.isSuccess()) {
            logger.info("??????fgoodsId {}?????????spu????????????", goodsDetailMallDto.getFgoodsId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        List<GoodsSku> spuStockSell = spuStockSellResult.getData();
        if (!CollectionUtils.isEmpty(spuStockSell)) {
            long sumSkuSellNum = spuStockSell.stream().mapToLong(GoodsSku::getFsellNum).sum();
            result.setFsellNum(sumSkuSellNum);
        }
        return result;
    }

    @Override
    public Result<Integer> getIsRegular(Long fgoodsId, Long fuid) {
        //?????????????????????????????? 1??? 0???
        Integer fisRegular = 0;
        //????????????????????????????????????
        Result<Integer> isRegularResult = regularListApi.countByCriteria(Criteria.of(RegularList.class)
                .andEqualTo(RegularList::getFgoodsId, fgoodsId)
                .andEqualTo(RegularList::getFuid, fuid));
        if (isRegularResult.isSuccess() && isRegularResult.getData() > 0) {
            fisRegular = 1;
        }
        return Result.success(fisRegular);
    }

    @Override
    public Result<List<CouponVo>> getSkuUserCouponLight(Long fskuId, Long fuid) {
        //?????????
        List<CouponVo> allCoupon = this.getAllSkuUserCoupon(fskuId, fuid);

        //????????????
        Map<String, Object> alreadyReceiveCoupon = this.getAlreadyReceiveCoupon(fskuId, fuid);
        List<CouponVo> receiveCoupon = (List<CouponVo>) alreadyReceiveCoupon.get("receiveCoupon");
        List<Long> alCouponIds = (List<Long>) alreadyReceiveCoupon.get("removeCoupon");
        //????????????
        List<CouponVo> unReceiceCoupon = allCoupon.stream().filter(item -> !alCouponIds.contains(item.getFcouponId())).collect(toList());

        List<CouponVo> allCouponShow = new ArrayList<>();
        allCouponShow.addAll(receiveCoupon);
        allCouponShow.addAll(unReceiceCoupon);

        List<CouponVo> collect = allCouponShow.stream().sorted(Comparator.comparing(CouponVo::getFthresholdAmount).reversed()).limit(5).collect(toList());
        this.dealAmount(collect);
        return Result.success(collect);
    }

    @Override
    public Result<GoodsDetailCouponVo> getSkuUserCoupon(Long fskuId, Long fuid) {
        //?????????????????????????????? ???????????????????????????
        GoodsDetailCouponVo result = new GoodsDetailCouponVo();
        //?????????
        List<CouponVo> allCoupon = this.getAllSkuUserCoupon(fskuId, fuid);
        //????????????
        Map<String, Object> alreadyReceiveCoupon = this.getAlreadyReceiveCoupon(fskuId, fuid);
        List<CouponVo> receiveCoupon = (List<CouponVo>) alreadyReceiveCoupon.get("receiveCoupon");
        List<Long> alCouponIds = (List<Long>) alreadyReceiveCoupon.get("removeCoupon");
        //????????????
        List<CouponVo> unReceiceCoupon = allCoupon.stream().filter(item -> !alCouponIds.contains(item.getFcouponId())).collect(toList());

        this.dealAmount(receiveCoupon);
        this.dealAmount(unReceiceCoupon);
        result.setReceiveCouponLis(receiveCoupon.stream().sorted(Comparator.comparing(CouponVo::getFthresholdAmount).reversed()).collect(toList()));
        result.setUnReceiveCouponLis(unReceiceCoupon.stream().sorted(Comparator.comparing(CouponVo::getFthresholdAmount).reversed()).collect(toList()));
        result.setNowDate(new Date());
        return Result.success(result);
    }

    //??????sku????????????????????????????????????????????????????????????
    private List<CouponVo> getAllSkuUserCoupon(Long fskuId, Long fuid) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<CouponVo> result = new ArrayList<>();
        Map<String, Object> userCondition = new HashMap<>(5);
        CouponQueryDto couponQueryDto = new CouponQueryDto();
        couponQueryDto.setReleaseTypes(Lists.newArrayList(CouponReleaseTypeEnum.PAGE_RECEIVE.getCode()));
        couponQueryDto.setSkuId(fskuId);
        Result<List<CouponQueryVo>> couponQueryResult = couponProviderApi.queryBySkuId(couponQueryDto);

        List<CouponQueryVo> couponQueryVos = couponQueryResult.getData();
        logger.info("??????sku??????????????????????????????{}??? skuid ={}", fskuId);
        if (!couponQueryResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        Date now = new Date();
        for (CouponQueryVo couponQueryVo : couponQueryVos) {
            Long fcouponId = couponQueryVo.getFcouponId();
            Result<Coupon> couponResult = couponApi.queryOneByCriteria(Criteria.of(Coupon.class)
                    .andEqualTo(Coupon::getFcouponId, fcouponId)
                    .andEqualTo(Coupon::getFcouponStatus, CouponStatusEnum.PUSHED.getCode())
                    .andEqualTo(Coupon::getFreleaseType, CouponReleaseTypeEnum.PAGE_RECEIVE.getCode())
                    .andEqualTo(Coupon::getFisShow, 1)
                    .fields(Coupon::getFcouponId, Coupon::getFcouponName, Coupon::getFcouponType, Coupon::getFthresholdAmount,
                            Coupon::getFdeductionValue, Coupon::getFvalidityStart, Coupon::getFvalidityEnd, Coupon::getFassignUser,
                            Coupon::getFapplicableSku, Coupon::getFvalidityType, Coupon::getFvalidityDays, Coupon::getFperLimit,
                            Coupon::getFreleaseTimeStart, Coupon::getFreleaseTimeEnd));
            Coupon coupon = couponResult.getData();
            //??????????????????????????????
            if (couponResult.isSuccess() && null != coupon) {
                if (coupon.getFvalidityType().equals(CouponValidityTypeEnum.TIME_SLOT.getCode())) {
                    Date fvalidityEnd = coupon.getFvalidityEnd();
                    String fvalidityEndStr = sdf.format(fvalidityEnd);
                    if (!fvalidityEndStr.equals("1970-01-01 00:00:00") && (now.after(fvalidityEnd))) {
                        continue;
                    }
                    Date freleaseTimeStart = coupon.getFreleaseTimeStart();
                    Date freleaseTimeEnd = coupon.getFreleaseTimeEnd();
                    String freleaseTimeStartStr = sdf.format(freleaseTimeStart);
                    if (!freleaseTimeStartStr.equals("1970-01-01 00:00:00") && (now.before(freleaseTimeStart) || now.after(freleaseTimeEnd))) {
                        continue;
                    }
                }
                // ???????????????1???????????????2?????????????????????3?????????????????????'
                int fassignUser = coupon.getFassignUser().intValue();
                if (fassignUser == 1) {
                    result.add(dozerMapper.map(coupon, CouponVo.class));
                    continue;
                } else if (fassignUser == 2) {
                    //????????????????????????fuid
                    Result<Integer> couponUserCount = this.getCouponUserCount(fcouponId.longValue(), fuid);
                    if (couponUserCount.isSuccess() && couponUserCount.getData() > 0) {
                        result.add(dozerMapper.map(coupon, CouponVo.class));
                        continue;
                    }
                    //?????????????????????
                    Result<List<CouponReleaseCondition>> couponUserAbleResult = this.getCouponUserAble(fcouponId.longValue());
                    if (!couponUserAbleResult.isSuccess()) {
                        throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                    }
                    List<CouponReleaseCondition> couponConditionLis = couponUserAbleResult.getData();
                    if (CollectionUtils.isEmpty(couponConditionLis)) {
                        continue;
                    }
                    this.setUserCondition(userCondition, fuid);

                    for (CouponReleaseCondition couponCondition : couponConditionLis) {
                        if (this.isUserCouponOk(couponCondition, userCondition)) {
                            result.add(dozerMapper.map(coupon, CouponVo.class));
                            break;
                        }
                    }
                } else {
                    //?????????????????????--?????????????????????????????????
                    //????????????????????????fuid
                    Result<Integer> couponUserCount = this.getCouponUserCount(fcouponId.longValue(), fuid);
                    if (couponUserCount.isSuccess() && couponUserCount.getData() == 0) {
                        //?????????????????????
                        Result<List<CouponReleaseCondition>> couponUserAbleResult = this.getCouponUserAble(fcouponId.longValue());
                        if (!couponUserAbleResult.isSuccess()) {
                            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                        }
                        List<CouponReleaseCondition> couponConditionLis = couponUserAbleResult.getData();
                        if (CollectionUtils.isEmpty(couponConditionLis)) {
                            continue;
                        }
                        this.setUserCondition(userCondition, fuid);
                        if (this.checkUserInCouponCondition(couponConditionLis, userCondition)) {
                            continue;
                        }
                        result.add(dozerMapper.map(coupon, CouponVo.class));
                    }
                }
            }
        }
        return result;
    }

    //?????????sku?????????
    private Map<String, Object> getAlreadyReceiveCoupon(Long fskuId, Long fuid) {
        Date now = new Date();
        //????????????????????????
        Result<List<CouponReceive>> couponReceResult = couponReceiveApi.queryByCriteria(Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFuid, fuid)
                .andEqualTo(CouponReceive::getFuserCouponStatus, CouponReceiveStatusEnum.NOT_USED.getCode())
                .fields(CouponReceive::getFcouponId, CouponReceive::getFvalidityStart, CouponReceive::getFvalidityEnd));
        if (!couponReceResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        List<CouponReceive> couponReceLis = couponReceResult.getData();
        List<CouponVo> receiveCoupon = new ArrayList<>(); //???sku?????????
        List<Long> removeCoupon = new ArrayList<>(); //?????????????????????????????????
        List<Long> isDealCouponLis = new ArrayList<>(); //????????????????????????
        Map<String, Long> skuCondition = new HashMap<>(5);
        if (!CollectionUtils.isEmpty(couponReceLis)) {
            for (CouponReceive couponReceive : couponReceLis) {
                Result<Coupon> couponResult = couponApi.queryOneByCriteria(Criteria.of(Coupon.class)
                        .andEqualTo(Coupon::getFcouponId, couponReceive.getFcouponId())
                        .andEqualTo(Coupon::getFcouponStatus, CouponStatusEnum.PUSHED.getCode())
                        .andEqualTo(Coupon::getFisShow, 1)
                        .fields(Coupon::getFcouponId, Coupon::getFcouponName, Coupon::getFcouponType, Coupon::getFthresholdAmount,
                                Coupon::getFdeductionValue, Coupon::getFapplicableSku, Coupon::getFperLimit));
                Coupon coupon = couponResult.getData();
                if (couponResult.isSuccess() && null != coupon) {
                    Long fcouponId = coupon.getFcouponId();
                    Integer fperLimit = coupon.getFperLimit();
                    Date fvalidityStart = couponReceive.getFvalidityStart();
                    Date fvalidityEnd = couponReceive.getFvalidityEnd();
                    coupon.setFvalidityStart(fvalidityStart);
                    coupon.setFvalidityEnd(fvalidityEnd);
                    //1???????????????2?????????????????????3?????????????????????
                    int ableSku = coupon.getFapplicableSku().intValue();
                    if (ableSku == 1) {
                        if (now.after(fvalidityStart) && now.before(fvalidityEnd)) {
                            receiveCoupon.add(dozerMapper.map(coupon, CouponVo.class));
                        }
                        if (!isDealCouponLis.contains(fcouponId) && !this.isCanReceive(fcouponId, fuid, fperLimit)) {
                            removeCoupon.add(fcouponId);
                        }
                        continue;
                    } else if (ableSku == 2) {
                        //?????????????????????skuid
                        Result<Integer> countCouponSku = this.getCouponSkuCount(fcouponId, fskuId);
                        if (countCouponSku.isSuccess() && countCouponSku.getData() > 0) {
                            if (now.after(fvalidityStart) && now.before(fvalidityEnd)) {
                                receiveCoupon.add(dozerMapper.map(coupon, CouponVo.class));
                            }
                            if (!isDealCouponLis.contains(fcouponId) && !this.isCanReceive(fcouponId, fuid, fperLimit)) {
                                removeCoupon.add(fcouponId);
                            }
                            continue;
                        }
                        //?????????????????????
                        Result<List<CouponApplicableSkuCondition>> skuConditionRes = this.getCouponSkuAble(fcouponId);
                        if (!skuConditionRes.isSuccess()) {
                            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                        }
                        List<CouponApplicableSkuCondition> couponConditionLis = skuConditionRes.getData();
                        if (CollectionUtils.isEmpty(couponConditionLis)) {
                            continue;
                        }
                        this.setSkuCondition(skuCondition, fskuId);
                        for (CouponApplicableSkuCondition couponCondition : couponConditionLis) {
                            if (this.isSkuCouponOk(couponCondition, skuCondition)) {
                                if (now.after(fvalidityStart) && now.before(fvalidityEnd)) {
                                    receiveCoupon.add(dozerMapper.map(coupon, CouponVo.class));
                                }
                                if (!isDealCouponLis.contains(fcouponId) && !this.isCanReceive(fcouponId, fuid, fperLimit)) {
                                    removeCoupon.add(fcouponId);
                                }
                                break;
                            }
                        }
                    } else {
                        //?????????????????????--sku??????????????????????????????
                        //?????????????????????skuid
                        Result<Integer> countCouponSku = this.getCouponSkuCount(couponReceive.getFcouponId(), fskuId);
                        if (countCouponSku.isSuccess() && countCouponSku.getData() == 0) {
                            //?????????????????????
                            Result<List<CouponApplicableSkuCondition>> skuConditionRes = this.getCouponSkuAble(couponReceive.getFcouponId());
                            if (!skuConditionRes.isSuccess()) {
                                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                            }
                            List<CouponApplicableSkuCondition> couponConditionLis = skuConditionRes.getData();
                            if (CollectionUtils.isEmpty(couponConditionLis)) {
                                continue;
                            }
                            this.setSkuCondition(skuCondition, fskuId);
                            if (this.checkSkuInCouponCondition(couponConditionLis, skuCondition)) {
                                continue;
                            }
                            if (now.after(fvalidityStart) && now.before(fvalidityEnd)) {
                                receiveCoupon.add(dozerMapper.map(coupon, CouponVo.class));
                            }
                            if (!isDealCouponLis.contains(fcouponId) && !this.isCanReceive(fcouponId, fuid, fperLimit)) {
                                removeCoupon.add(fcouponId);
                            }
                        }
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>(2);
        result.put("receiveCoupon", receiveCoupon);
        result.put("removeCoupon", removeCoupon);
        return result;
    }

    private Boolean checkUserInCouponCondition(List<CouponReleaseCondition> conditionList, Map<String, Object> userCondition) {
        boolean result = false;
        for (CouponReleaseCondition condition : conditionList) {
            //??????????????? ??????????????????
            result = result || isUserCouponOk(condition, userCondition);
        }
        return result;
    }

    private Boolean isUserCouponOk(CouponReleaseCondition couponCondition, Map<String, Object> userCondition) {
        //???????????????---?????????????????????
        boolean operateMatch = false;
        boolean userLevelMatch = false;
        boolean marketBdMatch = false;
        boolean createTimeMatch = false;
        boolean userValidTimeMatch = false;
        //?????????????????????
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<Integer> foperate_coupon = (List<Integer>) JSON.parse(couponCondition.getFoperateType());
        List<Long> fuserLevelId_coupon = (List<Long>) JSON.parse(couponCondition.getFuserLevelId());
        List<Long> fmarketBdId_coupon = (List<Long>) JSON.parse(couponCondition.getFmarketBdId());
        Date fuserRegisterTimeStart = couponCondition.getFuserRegisterTimeStart();
        Date fuserRegisterTimeEnd = couponCondition.getFuserRegisterTimeEnd();
        Date fuserValidTimeStart = couponCondition.getFuserValidTimeStart();
        Date fuserValidTimeEnd = couponCondition.getFuserValidTimeEnd();

        //fuid???????????????
        Integer operateType = (Integer) userCondition.get("operateType");
        Long userLevelId = (Long) userCondition.get("userLevelId");
        Long marketBdId = (Long) userCondition.get("marketBdId");
        Date createTime = (Date) userCondition.get("createTime");
        Date userValidTime = (Date) userCondition.get("userValidTime");

        if (CollectionUtils.isEmpty(foperate_coupon)) {
            operateMatch = true;
        } else {
            if (foperate_coupon.contains(operateType)) {
                operateMatch = true;
            }
        }

        if (CollectionUtils.isEmpty(fuserLevelId_coupon)) {
            userLevelMatch = true;
        } else {
            if (fuserLevelId_coupon.contains(userLevelId)) {
                userLevelMatch = true;
            }
        }

        if (CollectionUtils.isEmpty(fmarketBdId_coupon)) {
            marketBdMatch = true;
        } else {
            if (fmarketBdId_coupon.contains(marketBdId)) {
                marketBdMatch = true;
            }
        }

        if (createTime.after(fuserRegisterTimeStart) && createTime.before(fuserRegisterTimeEnd)) {
            createTimeMatch = true;
        }

        if (sdf.format(userValidTime).equals("1970-01-01 00:00:00")) {
            userValidTimeMatch = true;
        } else {
            if (userValidTime.after(fuserValidTimeStart) && userValidTime.before(fuserValidTimeEnd)) {
                userValidTimeMatch = true;
            }
        }
        return operateMatch && userLevelMatch && marketBdMatch && createTimeMatch && userValidTimeMatch;
    }

    private Boolean checkSkuInCouponCondition(List<CouponApplicableSkuCondition> conditionList, Map<String, Long> skuCondition) {
        boolean result = false;
        for (CouponApplicableSkuCondition condition : conditionList) {
            //??????????????? ??????????????????
            result = result || isSkuCouponOk(condition, skuCondition);
        }
        return result;
    }

    private Boolean isSkuCouponOk(CouponApplicableSkuCondition couponCondition, Map<String, Long> skuCondition) {
        //?????????????????????---???????????????---?????????????????????
        boolean categoryMatch = false;
        boolean brandMatch = false;
        boolean labelMatch = false;
        boolean tradeMatch = false;
        //???????????????
        List<Long> fbrandIds_coupon = JSON.parseObject(couponCondition.getFbrandId(), List.class);
        Map<String, List<Long>> fcategoryIds_coupon = JSON.parseObject(couponCondition.getFcategoryId(), Map.class);
        List<Long> flabelIds_coupon = JSON.parseObject(couponCondition.getFlabelId(), List.class);
        List<Long> ftradeId_coupon = JSON.parseObject(couponCondition.getFtradeCode(), List.class);

        //skuid???????????????
        Long brandId = skuCondition.get("brandId");
        Integer categoryId1 = skuCondition.get("categoryId1").intValue();
        Integer categoryId2 = skuCondition.get("categoryId2").intValue();
        Integer categoryId3 = skuCondition.get("categoryId3").intValue();
        Long labelId = skuCondition.get("labelId");
        Long tradeId = skuCondition.get("tradeId");

        if (Objects.isNull(fcategoryIds_coupon)) {
            categoryMatch = true;
        } else {
            if ((Objects.nonNull(fcategoryIds_coupon.get("1")) && fcategoryIds_coupon.get("1").contains(categoryId1)) || (Objects.nonNull(fcategoryIds_coupon.get("2")) && fcategoryIds_coupon.get("2").contains(categoryId2)) || (Objects.nonNull(fcategoryIds_coupon.get("3")) && fcategoryIds_coupon.get("3").contains(categoryId3))) {
                categoryMatch = true;
            }
        }

        if (CollectionUtils.isEmpty(fbrandIds_coupon)) {
            brandMatch = true;
        } else {
            if (CollectionUtils.isNotEmpty(fbrandIds_coupon) && fbrandIds_coupon.contains(brandId)) {
                brandMatch = true;
            }
        }

        if (CollectionUtils.isEmpty(flabelIds_coupon)) {
            labelMatch = true;
        } else {
            if (CollectionUtils.isNotEmpty(flabelIds_coupon) && flabelIds_coupon.contains(labelId)) {
                labelMatch = true;
            }
        }

        if (CollectionUtils.isEmpty(ftradeId_coupon)) {
            tradeMatch = true;
        } else {
            if (CollectionUtils.isNotEmpty(ftradeId_coupon) && ftradeId_coupon.contains(tradeId)) {
                tradeMatch = true;
            }
        }
        return categoryMatch && brandMatch && labelMatch && tradeMatch;
    }

    private Boolean isCanReceive(Long fcouponId, Long fuid, int fperLimit) {
        Result<Integer> countResult = couponReceiveApi.countByCriteria(Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFcouponId, fcouponId)
                .andEqualTo(CouponReceive::getFuid, fuid));
        Ensure.that(countResult.isSuccess()).isTrue(new MallPcExceptionCode(countResult.getCode(), countResult.getMsg()));
        int isReceive = countResult.getData().intValue();
        return isReceive < fperLimit ? true : false;
    }

    private Result<Integer> getCouponSkuCount(Long fcouponId, Long fskuId) {
        Result<Integer> countCouponSku = couponApplicableSkuApi.countByCriteria(Criteria.of(CouponApplicableSku.class)
                .andEqualTo(CouponApplicableSku::getFcouponId, fcouponId)
                .andEqualTo(CouponApplicableSku::getFskuId, fskuId));
        return countCouponSku;
    }

    private Result<List<CouponApplicableSkuCondition>> getCouponSkuAble(Long fcouponId) {
        Result<List<CouponApplicableSkuCondition>> skuConditionRes = couponApplicableSkuConditionApi.queryByCriteria(Criteria.of(CouponApplicableSkuCondition.class)
                .andEqualTo(CouponApplicableSkuCondition::getFcouponId, fcouponId)
                .fields(CouponApplicableSkuCondition::getFbrandId,
                        CouponApplicableSkuCondition::getFcategoryId,
                        CouponApplicableSkuCondition::getFlabelId,
                        CouponApplicableSkuCondition::getFtradeCode));
        return skuConditionRes;
    }

    private Map<String, Long> setSkuCondition(Map<String, Long> skuCondition, Long fskuId) {
        if (null == skuCondition.get("brandId")) {
            Result<GoodsSku> goodsSkuResult = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                    .andEqualTo(GoodsSku::getFskuId, fskuId)
                    .fields(GoodsSku::getFbrandId, GoodsSku::getFcategoryId1, GoodsSku::getFcategoryId2,
                            GoodsSku::getFcategoryId3, GoodsSku::getFlabelId, GoodsSku::getFgoodsId));
            GoodsSku goodsSku = goodsSkuResult.getData();
            if (goodsSkuResult.isSuccess() && null != goodsSku) {
                skuCondition.put("brandId", goodsSku.getFbrandId());
                skuCondition.put("categoryId1", goodsSku.getFcategoryId1());
                skuCondition.put("categoryId2", goodsSku.getFcategoryId2());
                skuCondition.put("categoryId3", goodsSku.getFcategoryId3());
                skuCondition.put("labelId", goodsSku.getFlabelId());
                Result<Goods> goodsResult = goodsApi.queryOneByCriteria(Criteria.of(Goods.class)
                        .andEqualTo(Goods::getFgoodsId, goodsSku.getFgoodsId())
                        .fields(Goods::getFtradeId));
                Goods goods = goodsResult.getData();
                if (goodsResult.isSuccess() && null != goods) {
                    skuCondition.put("tradeId", goods.getFtradeId());
                }
            }
        }
        return skuCondition;
    }

    private Result<Integer> getCouponUserCount(Long fcouponId, Long fuid) {
        Result<Integer> countCouponUser = couponReleaseApi.countByCriteria(Criteria.of(CouponRelease.class)
                .andEqualTo(CouponRelease::getFcouponId, fcouponId)
                .andEqualTo(CouponRelease::getFuid, fuid));
        return countCouponUser;
    }

    private Result<List<CouponReleaseCondition>> getCouponUserAble(Long fcouponId) {
        Result<List<CouponReleaseCondition>> userConditionRes = couponReleaseConditionApi.queryByCriteria(Criteria.of(CouponReleaseCondition.class)
                .andEqualTo(CouponReleaseCondition::getFcouponId, fcouponId)
                .fields(CouponReleaseCondition::getFoperateType, CouponReleaseCondition::getFuserLevelId,
                        CouponReleaseCondition::getFmarketBdId, CouponReleaseCondition::getFuserValidTimeStart,
                        CouponReleaseCondition::getFuserValidTimeEnd, CouponReleaseCondition::getFuserRegisterTimeStart,
                        CouponReleaseCondition::getFuserRegisterTimeEnd));
        return userConditionRes;
    }

    private Map<String, Object> setUserCondition(Map<String, Object> userCondition, Long fuid) {
        if (null == userCondition.get("operateType")) {
            Result<User> userResult = userApi.queryOneByCriteria(Criteria.of(User.class)
                    .andEqualTo(User::getFuid, fuid)
                    .fields(User::getFoperateType, User::getFuserLevelId, User::getFmarketBdId,
                            User::getFcreateTime, User::getFverifyStatus, User::getFuserValidTime));
            User user = userResult.getData();
            if (userResult.isSuccess() && null != user) {
                userCondition.put("operateType", user.getFoperateType());
                userCondition.put("userLevelId", user.getFuserLevelId());
                userCondition.put("marketBdId", user.getFmarketBdId());
                userCondition.put("createTime", user.getFcreateTime());
                userCondition.put("verifyStatus", user.getFverifyStatus());
                userCondition.put("userValidTime", user.getFuserValidTime());
            }
        }
        return userCondition;
    }

    private List<CouponVo> dealAmount(List<CouponVo> result) {
        for (CouponVo couponMap : result) {
            couponMap.setFthresholdAmount(PriceUtil.toYuan(couponMap.getFthresholdAmount()));
            //??????????????????1????????????2?????????
            if (couponMap.getFcouponType().equals(CouponTypeEnum.FULL_REDUCTION.getCode())) {
                couponMap.setFdeductionValue(PriceUtil.toYuan(couponMap.getFdeductionValue()));
            } else {
                couponMap.setFdeductionValue(couponMap.getFdeductionValue().divide(new BigDecimal("100"), 2, BigDecimal.ROUND_DOWN));
            }
        }
        return result;
    }

    @Override
    public Result<String> getCouponInstructions(Long fcouponId) {
        Result<Coupon> couponResult = couponApi.queryOneByCriteria(Criteria.of(Coupon.class)
                .andEqualTo(Coupon::getFcouponId, fcouponId)
                .fields(Coupon::getFapplicableSku));
        if (!couponResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null == couponResult.getData()) {
            Result.failure(MallPcExceptionCode.COUPON_IS_NOT_EXIST);
        }
        String result = "";
        //1???????????????2?????????????????????3?????????????????????
        int fapplicableSku = couponResult.getData().getFapplicableSku().intValue();
        if (1 == fapplicableSku) {
            result = "??????????????????";
        } else if (2 == fapplicableSku) {
            if (this.isHasCouponSkuCondition(fcouponId)) {
                result = "??????????????????" + "\n" + this.getCouponSkuCondition(fcouponId, true);
            } else {
                result = "??????????????????";
            }
        } else {
            if (this.isHasCouponSkuCondition(fcouponId)) {
                result = "?????????????????????" + "\n" + this.getCouponSkuCondition(fcouponId, false);
            } else {
                result = "?????????????????????";
            }
        }
        return Result.success(result);
    }

    private Boolean isHasCouponSkuCondition(Long fcouponId) {
        Result<Integer> countResult = couponApplicableSkuConditionApi.countByCriteria(Criteria.of(CouponApplicableSkuCondition.class)
                .andEqualTo(CouponApplicableSkuCondition::getFcouponId, fcouponId));
        if (!countResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null == countResult.getData()) {
            Result.failure(MallPcExceptionCode.COUPON_IS_NOT_EXIST);
        }
        if (countResult.getData() > 0) {
            return true;
        } else {
            return false;
        }
    }

    private String getCouponSkuCondition(Long fcouponId, Boolean isCanBuy) {
        String result = "";
        //????????????--????????????--??????
        Result<CouponApplicableSkuCondition> conditionResult = couponApplicableSkuConditionApi.queryOneByCriteria(Criteria.of(CouponApplicableSkuCondition.class)
                .andEqualTo(CouponApplicableSkuCondition::getFcouponId, fcouponId)
                .fields(CouponApplicableSkuCondition::getFbrandName,
                        CouponApplicableSkuCondition::getFcategoryName,
                        CouponApplicableSkuCondition::getFtradeName,
                        CouponApplicableSkuCondition::getFlabelName));
        CouponApplicableSkuCondition conditionData = conditionResult.getData();
        if (!conditionResult.isSuccess() || null == conditionData) {
            return result;
        }
        StringBuffer resBf = new StringBuffer();
        String fcategoryName = conditionData.getFcategoryName();
        if (!StringUtils.isEmpty(fcategoryName)) {
            if (isCanBuy) {
                resBf.append("?????????").append("\n").append("??????????????? ").append(fcategoryName.substring(1, fcategoryName.length() - 1)).append("\n");
            } else {
                resBf.append("?????????").append("\n").append(fcategoryName.substring(1, fcategoryName.length() - 1)).append("????????? ").append("\n");
            }

        }
        String fbrandName = conditionData.getFbrandName();
        if (!StringUtils.isEmpty(fbrandName)) {
            if (isCanBuy) {
                resBf.append("?????????").append("\n").append("??????????????? ").append(fbrandName.substring(1, fbrandName.length() - 1)).append("\n");
            } else {
                resBf.append("?????????").append("\n").append(fbrandName.substring(1, fbrandName.length() - 1)).append("?????????").append("\n");
            }

        }
        String ftradeName = conditionData.getFtradeName();
        if (!StringUtils.isEmpty(ftradeName)) {
            if (isCanBuy) {
                resBf.append("???????????????").append("\n").append("??????????????? ").append(ftradeName.substring(1, ftradeName.length() - 1)).append("\n");
            } else {
                resBf.append("???????????????").append("\n").append(ftradeName.substring(1, ftradeName.length() - 1)).append("?????????").append("\n");
            }
        }
        String flabelName = conditionData.getFlabelName();
        if (!StringUtils.isEmpty(flabelName)) {
            if (isCanBuy) {
                resBf.append("?????????").append("\n").append("??????????????? ").append(flabelName.substring(1, flabelName.length() - 1));
            } else {
                resBf.append("?????????").append("\n").append(flabelName.substring(1, flabelName.length() - 1)).append("?????????");
            }
        }
        result = resBf.toString();
        return result;
    }

    @Override
    public Result addReceiveCoupon(Long fcouponId, Long fuid) {
        if (null == fcouponId || null == fuid) {
            return Result.failure(MallPcExceptionCode.PARAM_ERROR);
        }
        //???????????????--?????????????????????--????????????????????????--????????????--?????????????????????--??????????????????
        Result<Coupon> couponResult = couponApi.queryOneByCriteria(Criteria.of(Coupon.class)
                .andEqualTo(Coupon::getFcouponId, fcouponId)
                .andEqualTo(Coupon::getFcouponStatus, CouponStatusEnum.PUSHED.getCode())
                .andEqualTo(Coupon::getFreleaseType, CouponReleaseTypeEnum.PAGE_RECEIVE.getCode())
                .fields(Coupon::getFperLimit, Coupon::getFsurplusReleaseQty, Coupon::getFvalidityType,
                        Coupon::getFvalidityEnd, Coupon::getFreleaseTimeEnd));
        if (!couponResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        Coupon coupon = couponResult.getData();
        if (null == coupon) {
            return Result.failure(MallPcExceptionCode.COUPON_IS_NOT_EXIST);
        }
        if (coupon.getFsurplusReleaseQty() <= 0) {
            return Result.failure(MallPcExceptionCode.COUPON_IS_PAID_OUT);
        }
        Date now = new Date();
        if (coupon.getFvalidityType() == 1 && now.after(coupon.getFvalidityEnd())) {
            return Result.failure(MallPcExceptionCode.COUPON_IS_INVALID);
        }
        //??????????????????????????????
        Result<Integer> countResult = couponReceiveApi.countByCriteria(Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFuid, fuid)
                .andEqualTo(CouponReceive::getFcouponId, fcouponId));
        if (!couponResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null != countResult.getData() && countResult.getData().equals(coupon.getFperLimit())) {
            return Result.failure(MallPcExceptionCode.COUPON_IS_MAX);
        }
        ReceiveCouponDto receiveCouponDto = new ReceiveCouponDto();
        receiveCouponDto.setFuid(fuid);
        receiveCouponDto.setFcouponId(fcouponId);
        Result result = this.receiveCoupon(receiveCouponDto);
        return Result.success(result.getData());
    }

    @GlobalTransactional
    @Override
    public Result receiveCoupon(ReceiveCouponDto receiveCouponDto) {
        Long fcouponId = receiveCouponDto.getFcouponId();
        Long fuid = receiveCouponDto.getFuid();
        if (null == fcouponId || null == fuid) {
            return Result.failure(MallPcExceptionCode.PARAM_ERROR);
        }
        String lockKey = StringUtils.join(Lists.newArrayList(MallPcConstants.MALL_RECEIVE_COUPON, fcouponId, fuid), ":");
        String lockValue = RandomUtils.getUUID();
        try {
//            //??????????????????????????????
            Ensure.that(xybbcLock.tryLockTimes(lockKey, lockValue, 3, 6)).isTrue(MallPcExceptionCode.SYSTEM_BUSY_ERROR);
//            CouponBindUser couponBindUser = new CouponBindUser();
//            couponBindUser.setFcouponId(fcouponId);
//            couponBindUser.setFuid(fuid);
//            couponBindUser.setFcreateTime(new Date());
//            couponBindUser.setFisReceived(1);
//            Result<Integer> insertBindResult = couponBindUserApi.create(couponBindUser);
//            if (!insertBindResult.isSuccess()) {
//                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
//            }
            //???????????????????????????
            CouponReleaseDto couponReleaseDto = new CouponReleaseDto();
            couponReleaseDto.setCouponScene(CouponScene.PAGE_RECEIVE);
            couponReleaseDto.setCouponId(fcouponId);
            couponReleaseDto.setUserId(fuid);
            couponReleaseDto.setAlreadyReceived(true);
            couponReleaseDto.setDeltaValue(-1);
            Result updateReleaseResult = couponProviderApi.updateReleaseQty(couponReleaseDto);
            Ensure.that(updateReleaseResult.isSuccess()).isTrue(new MallPcExceptionCode(updateReleaseResult.getCode(), updateReleaseResult.getMsg()));
            //??????????????????
            Result receiveReceive = couponProviderApi.receive(couponReleaseDto);
            Ensure.that(receiveReceive.isSuccess()).isTrue(new MallPcExceptionCode(receiveReceive.getCode(), receiveReceive.getMsg()));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            xybbcLock.releaseLock(lockKey, lockValue);
        }
        return Result.success(true);
    }

    //    @Override
//    public Result<List<GoodsSkuBatchVo>> getSkuBatchSpecifi(Long fskuId) {
//        Result<List<SkuBatch>> skuBatchResult = skuBatchApi.queryByCriteria(Criteria.of(SkuBatch.class)
//                .andEqualTo(SkuBatch::getFskuId, fskuId)
//                .fields(SkuBatch::getFvalidityStartDate, SkuBatch::getFvalidityEndDate, SkuBatch::getFsupplierSkuBatchId, SkuBatch::getFskuId));
//        List<GoodsSkuBatchVo> convert = dozerHolder.convert(skuBatchResult.getData(), GoodsSkuBatchVo.class);
//        return Result.success(convert);
//    }
//
//    @Override
//    public Result<List<GoodsSkuBatchPackageVo>> getSkuBatchPackageSpecifi(Long fskuBatchId) {
//        Result<List<SkuBatchPackage>> skuBatchPackageResult = skuBatchPackageApi.queryByCriteria(Criteria.of(SkuBatchPackage.class)
//                .andEqualTo(SkuBatchPackage::getFsupplierSkuBatchId, fskuBatchId)
//                .fields(SkuBatchPackage::getFbatchPackageId, SkuBatchPackage::getFsupplierSkuBatchId, SkuBatchPackage::getFbatchPackageNum, SkuBatchPackage::getFbatchStartNum));
//        List<GoodsSkuBatchPackageVo> convert = dozerHolder.convert(skuBatchPackageResult.getData(), GoodsSkuBatchPackageVo.class);
//        return Result.success(convert);
//    }
    /**
     * @param skuId
     * @return
     *
     */
    @Override
    public Result<Map<String, Long>> getCategoryBySkuId(Long skuId) {

        Result<GoodsSku> goodsSkuResult = goodsSkuApi.queryById(skuId);
        if (!goodsSkuResult.isSuccess()) {
            throw new BizException(MallPcExceptionCode.SYSTEM_ERROR);
        }
        GoodsSku goodsSku = goodsSkuResult.getData();
        if (goodsSku == null) {
            throw new BizException(new MallPcExceptionCode("9999", "sku?????????"));
        }
        Map<String, Long> categorys = new HashMap<>(4);
        categorys.put("1", goodsSku.getFcategoryId1());
        categorys.put("2", goodsSku.getFcategoryId2());
        categorys.put("3", goodsSku.getFcategoryId3());
        return Result.success(categorys);
    }

    private SupplierWarehouse getSupplierWarehouseById(Long warehouseId) {
        Result<SupplierWarehouse> warehouseResult = warehouseApi.queryById(warehouseId);
        if (!warehouseResult.isSuccess()) {
            throw new BizException(MallPcExceptionCode.SYSTEM_ERROR);
        }
        return warehouseResult.getData();
    }

    private SupplierSkuBatch getSkuBatchById(String skuBatchId) {
        Result<SupplierSkuBatch> skuBatchResult = supplierSkuBatchApi.queryOneByCriteria(Criteria
                .of(SupplierSkuBatch.class).andEqualTo(SupplierSkuBatch::getFsupplierSkuBatchId, skuBatchId));
        if (!skuBatchResult.isSuccess()) {
            throw new BizException(MallPcExceptionCode.SYSTEM_ERROR);
        }
        return skuBatchResult.getData();
    }
}
