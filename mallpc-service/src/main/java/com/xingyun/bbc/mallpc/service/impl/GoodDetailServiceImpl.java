package com.xingyun.bbc.mallpc.service.impl;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.xingyun.bbc.core.activity.api.CouponProviderApi;
import com.xingyun.bbc.core.activity.enums.CouponScene;
import com.xingyun.bbc.core.activity.model.dto.CouponQueryDto;
import com.xingyun.bbc.core.activity.model.dto.CouponReleaseDto;
import com.xingyun.bbc.core.activity.model.vo.CouponQueryVo;
import com.xingyun.bbc.core.enums.ResultStatus;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.market.api.*;
import com.xingyun.bbc.core.market.enums.CouponReceiveStatusEnum;
import com.xingyun.bbc.core.market.enums.CouponReleaseTypeEnum;
import com.xingyun.bbc.core.market.enums.CouponStatusEnum;
import com.xingyun.bbc.core.market.enums.CouponTypeEnum;
import com.xingyun.bbc.core.market.po.*;
import com.xingyun.bbc.core.operate.api.CityRegionApi;
import com.xingyun.bbc.core.operate.api.CountryApi;
import com.xingyun.bbc.core.operate.enums.TradeTypeEnums;
import com.xingyun.bbc.core.operate.po.CityRegion;
import com.xingyun.bbc.core.operate.po.Country;
import com.xingyun.bbc.core.order.api.RegularListApi;
import com.xingyun.bbc.core.order.po.RegularList;
import com.xingyun.bbc.core.query.Criteria;
import com.xingyun.bbc.core.sku.api.*;
import com.xingyun.bbc.core.sku.enums.GoodsSkuEnums;
import com.xingyun.bbc.core.sku.enums.SkuBatchEnums;
import com.xingyun.bbc.core.sku.po.GoodsSku;
import com.xingyun.bbc.core.sku.po.*;
import com.xingyun.bbc.core.user.api.UserApi;
import com.xingyun.bbc.core.user.api.UserDeliveryApi;
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
import com.xingyun.bbc.mallpc.model.dto.detail.GoodsDetailMallDto;
import com.xingyun.bbc.mallpc.model.dto.detail.ReceiveCouponDto;
import com.xingyun.bbc.mallpc.model.dto.detail.SkuDiscountTaxDto;
import com.xingyun.bbc.mallpc.model.vo.detail.*;
import com.xingyun.bbc.mallpc.service.GoodDetailService;
import com.xingyun.bbc.order.api.FreightApi;
import com.xingyun.bbc.order.model.dto.freight.FreightDto;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dozer.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private UserApi userApi;

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

    @Autowired
    private Mapper dozerMapper;

    @Autowired
    private DozerHolder dozerHolder;

    @Autowired
    private XybbcLock xybbcLock;

    @Override
    public Result<List<String>> getGoodDetailPic(Long fgoodsId, Long fskuId) {
        List<String> result = new ArrayList<>();
        //查询sku图片
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
        //查询spu图片
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

    @Override
    public Result<GoodsVo> getGoodDetailBasic(Long fgoodsId, Long fskuId) {
        //获取商品spu基本信息
        Result<Goods> goodsBasic = goodsApi.queryById(fgoodsId);
        if (!goodsBasic.isSuccess()) {
            logger.info("商品spu id {}获取商品基本信息调用远程服务失败", fgoodsId);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null == goodsBasic.getData()) {
            return Result.success(null);
        }
        GoodsVo goodsVo = dozerMapper.map(goodsBasic.getData(), GoodsVo.class);

        //获取贸易类型名称
        String tradeType = TradeTypeEnums.getTradeType(goodsVo.getFtradeId().toString());
        if (null == tradeType) {
            logger.info("商品spu id {}获取商品贸易类型枚举失败", fgoodsId);
            throw new BizException(ResultStatus.NOT_IMPLEMENTED);
        }
        goodsVo.setFtradeType(tradeType);

        //获取sku商品描述和商品主图
        goodsVo.setFskuDesc("");
        if (null != fskuId) {
            GoodsSku goodSkuDesc = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                    .andEqualTo(GoodsSku::getFskuId, fskuId)
                    .fields(GoodsSku::getFskuDesc, GoodsSku::getFskuThumbImage)).getData();
            if (null != goodSkuDesc && null != goodSkuDesc.getFskuDesc()) {
                goodsVo.setFskuDesc(goodSkuDesc.getFskuDesc());
            }
            //之前取spu表列表缩略图后改成sku表主图
            if (null != goodSkuDesc && null != goodSkuDesc.getFskuThumbImage()) {
                goodsVo.setFgoodsImgUrl(goodSkuDesc.getFskuThumbImage());
            }
        }

        //获取品牌名称和国旗icon
        goodsVo.setFbrandName("");
        goodsVo.setFcountryIcon("");
        if (null != goodsVo.getFbrandId()) {
            GoodsBrand goodsBrand = goodsBrandApi.queryOneByCriteria(Criteria.of(GoodsBrand.class)
                    .andEqualTo(GoodsBrand::getFbrandId, goodsVo.getFbrandId())
                    .fields(GoodsBrand::getFbrandName, GoodsBrand::getFbrandLogo, GoodsBrand::getFcountryName)).getData();
            if (null != goodsBrand) {
                goodsVo.setFbrandName(goodsBrand.getFbrandName());
                goodsVo.setFbrandLogo(goodsBrand.getFbrandLogo() == null ? "" : goodsBrand.getFbrandLogo());
                Country country = countryApi.queryOneByCriteria(Criteria.of(Country.class)
                        .andEqualTo(Country::getFcountryName, goodsBrand.getFcountryName())
                        .fields(Country::getFcountryIcon)).getData();
                if (null != country) {
                    goodsVo.setFcountryIcon(country.getFcountryIcon());
                }
            }
        }

        //获取商品原产地名称
        goodsVo.setFgoodsOrigin("");
        if (null != goodsVo.getForiginId()) {
            CityRegion cityRegion = cityRegionApi.queryOneByCriteria(Criteria.of(CityRegion.class)
                    .andEqualTo(CityRegion::getFregionId, goodsVo.getForiginId())
                    .fields(CityRegion::getFcrName)).getData();
            if (null != cityRegion && null != cityRegion.getFcrName()) {
                goodsVo.setFgoodsOrigin(cityRegion.getFcrName());
            }
        }

//        //商品规格
//        Result<List<GoodsSku>> goodsSkuResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
//                .andEqualTo(GoodsSku::getFgoodsId, fgoodsId)
//                .fields(GoodsSku::getFskuId, GoodsSku::getFgoodsId, GoodsSku::getFskuSpecValue));
//        List<GoodsSkuVo> convert = dozerHolder.convert(goodsSkuResult.getData(), GoodsSkuVo.class);
//        goodsVo.setFgoodsSkuVo(convert);
        return Result.success(goodsVo);
    }


    @Override
    public Result<GoodspecificationVo> getGoodsSpecifi(Long fgoodsId) {
        //商品各种规格
        GoodspecificationVo result = new GoodspecificationVo();

        //sku规格
        Result<List<GoodsSku>> goodsSkuResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFgoodsId, fgoodsId)
                .andEqualTo(GoodsSku::getFskuStatus, GoodsSkuEnums.Status.OnShelves.getValue())
                .andEqualTo(GoodsSku::getFisDelete, "0")
                .fields(GoodsSku::getFskuId, GoodsSku::getFskuCode, GoodsSku::getFskuSpecValue));
        List<GoodsSkuVo> skuRes = dozerHolder.convert(goodsSkuResult.getData(), GoodsSkuVo.class);
        //批次效期
        List<GoodsSkuBatchVo> batchRes = new ArrayList<>();
        //包装规格
        List<GoodsSkuBatchPackageVo> packageRes = new ArrayList<>();
        //商品各种规格详情
        List<GoodspecificationDetailVo> detailRes = new ArrayList<>();

        for (GoodsSkuVo skuVo : skuRes) {
            Result<List<SkuBatch>> skuBatchResult = skuBatchApi.queryByCriteria(Criteria.of(SkuBatch.class)
                    .andEqualTo(SkuBatch::getFskuId, skuVo.getFskuId())
                    .andEqualTo(SkuBatch::getFbatchStatus, SkuBatchEnums.Status.OnShelves.getValue())
                    .andEqualTo(SkuBatch::getFbatchPutwaySort, 1)//只用取上架排序为1的
                    .fields(SkuBatch::getFqualityEndDate, SkuBatch::getFsupplierSkuBatchId));
            List<GoodsSkuBatchVo> batchVert = dozerHolder.convert(skuBatchResult.getData(), GoodsSkuBatchVo.class);
            batchRes.addAll(batchVert);
            for (GoodsSkuBatchVo batchVo : batchVert) {
                Result<List<SkuBatchPackage>> skuBatchPackageResult = skuBatchPackageApi.queryByCriteria(Criteria.of(SkuBatchPackage.class)
                        .andEqualTo(SkuBatchPackage::getFsupplierSkuBatchId, batchVo.getFsupplierSkuBatchId())
                        .fields(SkuBatchPackage::getFbatchPackageId, SkuBatchPackage::getFbatchPackageNum, SkuBatchPackage::getFbatchStartNum));
                List<GoodsSkuBatchPackageVo> packageVert = dozerHolder.convert(skuBatchPackageResult.getData(), GoodsSkuBatchPackageVo.class);
                packageRes.addAll(packageVert);
                for (GoodsSkuBatchPackageVo packageVo : packageVert) {
                    GoodspecificationDetailVo detailVo = new GoodspecificationDetailVo();
                    detailVo.setFskuId(skuVo.getFskuId());
                    detailVo.setFskuCode(skuVo.getFskuCode());
                    detailVo.setFskuSpecValue(skuVo.getFskuSpecValue());
                    detailVo.setFskuBatchId(batchVo.getFsupplierSkuBatchId());
                    detailVo.setFqualityEndDate(batchVo.getFqualityEndDate());
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

        //移动端老哥一定要拼成这样 一定要灵活避免经常发前端版本 ^_^！#
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
        skuEx.setTitle("规格");
        skuEx.setItem(skuMall);

        for (GoodsSkuBatchVo batchRe : batchRes) {
            MallTVo tVoBatch = new MallTVo();
            tVoBatch.setTId(batchRe.getFsupplierSkuBatchId());
            tVoBatch.setTName(new SimpleDateFormat("yyyy-MM-dd").format(batchRe.getFqualityEndDate()));
            batchMall.add(tVoBatch);
        }
        GoodspecificationExVo batchEx = new GoodspecificationExVo();
        batchEx.setIdType(2);
        batchEx.setKeyType("goodsSkuBatchVoLis");
        batchEx.setTitle("效期");
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
        packageEx.setTitle("件装");
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


    @Override
    public Result<Map<String, List<GoodsAttributeVo>>> getGoodsAttribute(Long fgoodsId) {
        //获取商品属性
        Result<List<GoodsAttribute>> goodsAttributeRes = goodsAttributeApi.queryByCriteria(Criteria.of(GoodsAttribute.class)
                .andEqualTo(GoodsAttribute::getFgoodsId, fgoodsId)
                .fields(GoodsAttribute::getFclassAttributeItemVal, GoodsAttribute::getFclassAttributeId, GoodsAttribute::getFclassAttributeName));
        List<GoodsAttributeVo> convert = dozerHolder.convert(goodsAttributeRes.getData(), GoodsAttributeVo.class);

        Map<String, List<GoodsAttributeVo>> collect = convert.stream().collect(Collectors.groupingBy(GoodsAttributeVo::getFclassAttributeName, toList()));
        return Result.success(collect);
    }

    @Override
    public Result<GoodsPriceVo> getGoodPrice(GoodsDetailMallDto goodsDetailMallDto) {
        //查询用户认证类型
        Result<User> userResult = userApi.queryOneByCriteria(Criteria.of(User.class)
                .andEqualTo(User::getFuid, goodsDetailMallDto.getFuid())
                .fields(User::getFoperateType, User::getFverifyStatus));
        if (!userResult.isSuccess() || null == userResult.getData()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        //如果用户未认证直接查基础价格表
        goodsDetailMallDto.setFoperateType(userResult.getData().getFoperateType());
        goodsDetailMallDto.setFverifyStatus(userResult.getData().getFverifyStatus());

        //获取价格地址
        GoodsPriceVo priceResult = new GoodsPriceVo();
        //到规格
        if (null != goodsDetailMallDto.getFbatchPackageId()) {
            BigDecimal packagePrice = this.getPackagePrice(goodsDetailMallDto);
            priceResult.setRealPrice(PriceUtil.toYuan(packagePrice));
            priceResult.setPriceStart(PriceUtil.toYuan(packagePrice));
        }
        //到批次
        if (null != goodsDetailMallDto.getFsupplierSkuBatchId() && null == goodsDetailMallDto.getFbatchPackageId()) {
            priceResult = this.getBatchPrice(goodsDetailMallDto);
            this.dealGoodDetailPriceToYuan(priceResult);
        }
        //到sku
        if (null != goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId() && null == goodsDetailMallDto.getFbatchPackageId()) {
            priceResult = this.getSkuPrice(goodsDetailMallDto);
            this.dealGoodDetailPriceToYuan(priceResult);
        }
        //到spu
        if (null != goodsDetailMallDto.getFgoodsId() && null == goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId() && null == goodsDetailMallDto.getFbatchPackageId()) {
            priceResult = this.getSpuPrice(goodsDetailMallDto);
            this.dealGoodDetailPriceToYuan(priceResult);
        }
        //起始区间价 只有是单一价格PriceStart才计算运费、税费、折合单价
        if (null == priceResult.getPriceEnd()) {
            //查询批次价格类型 1.含邮含税 2.含邮不含税 3.不含邮含税 4.不含邮不含税
            SkuBatch fskuBatch = skuBatchApi.queryOneByCriteria(Criteria.of(SkuBatch.class)
                    .andEqualTo(SkuBatch::getFsupplierSkuBatchId, goodsDetailMallDto.getFsupplierSkuBatchId())
                    .fields(SkuBatch::getFbatchPriceType, SkuBatch::getFfreightId, SkuBatch::getFsupplierSkuBatchId)).getData();
            Integer fbatchPriceType = fskuBatch.getFbatchPriceType();
            //运费先判断--价格类型--不含邮才计算运费
            BigDecimal freightPrice = BigDecimal.ZERO;
            if (fbatchPriceType.intValue() == 3 || fbatchPriceType.intValue() == 4) {
                // 判断是默认地址还是前端选中的地址
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
                        //使用默认地址计算运费
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
                    //使用前端传参计算运费
                    if (null != goodsDetailMallDto.getFdeliveryCityId() && null != goodsDetailMallDto.getFnum()) {
                        freightPrice = this.getFreight(goodsDetailMallDto.getFbatchPackageId(), fskuBatch.getFfreightId(),
                                goodsDetailMallDto.getFdeliveryCityId(), fskuBatch.getFsupplierSkuBatchId(), goodsDetailMallDto.getFnum());
                    }
                }
            }

            //查询税率
            Long fskuTaxRate = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                    .andEqualTo(GoodsSku::getFskuId, goodsDetailMallDto.getFskuId())
                    .fields(GoodsSku::getFskuTaxRate)).getData().getFskuTaxRate();
            //(原始价*购买数量)
            BigDecimal orgPrice = priceResult.getPriceStart().multiply(new BigDecimal(goodsDetailMallDto.getFnum()));
            //税费 = (原始价*购买数量 + 运费) * 税率
            BigDecimal taxPrice = BigDecimal.ZERO;

            if (fbatchPriceType.intValue() == 2 || fbatchPriceType.intValue() == 4) {
                taxPrice = orgPrice.add(freightPrice).multiply(new BigDecimal(fskuTaxRate))
                        .divide(MallPcConstants.TEN_THOUSAND, 2, BigDecimal.ROUND_HALF_UP);
            }
            //总价 = (原始价*购买数量) + 运费 + 税费
            BigDecimal priceTotal = orgPrice.add(freightPrice).add(taxPrice);
            //折合单价 = 总价 / 数量 /包装规格数量
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

    private BigDecimal getFreight(Long fbatchPackageId, Long ffreightId, Long fdeliveryCityId, String fsupplierSkuBatchId, Long fnum) {
        BigDecimal freightPrice = BigDecimal.ZERO;
        //查询相应规格的件装数
        Result<SkuBatchPackage> skuBatchPackageResult = skuBatchPackageApi.queryOneByCriteria(Criteria.of(SkuBatchPackage.class)
                .andEqualTo(SkuBatchPackage::getFbatchPackageId, fbatchPackageId)
                .fields(SkuBatchPackage::getFbatchPackageNum));
        if (!skuBatchPackageResult.isSuccess()) {
            logger.info("批次包装规格fbatchPackageId {}获取包装规格值失败", fbatchPackageId);
            throw new BizException(MallPcExceptionCode.BATCH_PACKAGE_NUM_NOT_EXIST);
        }
        Long fbatchPackageNum = skuBatchPackageResult.getData().getFbatchPackageNum();
        FreightDto freightDto = new FreightDto();
        freightDto.setFfreightId(ffreightId);
        freightDto.setFregionId(fdeliveryCityId);
        freightDto.setFbatchId(fsupplierSkuBatchId);
        freightDto.setFbuyNum(fnum * fbatchPackageNum);
        logger.info("商品详情--查询运费入参{}", JSON.toJSONString(freightDto));
        Result<BigDecimal> bigDecimalResult = freightApi.queryFreight(freightDto);
        if (bigDecimalResult.isSuccess() && null != bigDecimalResult.getData()) {
            freightPrice = bigDecimalResult.getData().divide(MallPcConstants.ONE_HUNDRED, 2, BigDecimal.ROUND_HALF_UP);
        }
        return freightPrice;
    }

    private void dealGoodDetailPriceToYuan (GoodsPriceVo goodsPriceVo) {
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

    //获取到规格的价格
    private BigDecimal getPackagePrice(GoodsDetailMallDto goodsDetailMallDto) {
        BigDecimal price = BigDecimal.ZERO;
        //用户认证类型
        Integer foperateType = goodsDetailMallDto.getFoperateType();
        //如果未认证直接查基础表
        if (goodsDetailMallDto.getFverifyStatus().intValue() == UserVerifyStatusEnum.AUTHENTICATED.getCode()) {
            //sku是否支持平台会员折扣
            if (null == goodsDetailMallDto.getFskuDiscount()) {
                if (this.getIsSkuDiscountTax(goodsDetailMallDto.getFskuId()).getFisUserTypeDiscount().intValue() == 1) {
                    //sku——user是否支持折扣
                    return this.getDiscountPriceFinal(goodsDetailMallDto, foperateType);
                } else {
                    return this.getGeneralPrice(goodsDetailMallDto.getFbatchPackageId());
                }
            } else {
                if (goodsDetailMallDto.getFskuDiscount().intValue() == 1) {
                    //sku——user是否支持折扣
                    return this.getDiscountPriceFinal(goodsDetailMallDto, foperateType);
                } else {
                    return this.getGeneralPrice(goodsDetailMallDto.getFbatchPackageId());
                }
            }
        } else {
            return this.getGeneralPrice(goodsDetailMallDto.getFbatchPackageId());
        }
    }

    //获取非折扣价格
    private BigDecimal getGeneralPrice(Long fbatchPackageId) {
        Long price = 0L;
        Result<GoodsSkuBatchPrice> goodsSkuBatchPriceResult = goodsSkuBatchPriceApi.queryOneByCriteria(Criteria.of(GoodsSkuBatchPrice.class)
                .andEqualTo(GoodsSkuBatchPrice::getFbatchPackageId, fbatchPackageId)
                .fields(GoodsSkuBatchPrice::getFbatchSellPrice));
        if (goodsSkuBatchPriceResult.isSuccess() && null != goodsSkuBatchPriceResult.getData()) {
            if (null != goodsSkuBatchPriceResult.getData().getFbatchSellPrice()) {
                price = goodsSkuBatchPriceResult.getData().getFbatchSellPrice();
            }
        }
        BigDecimal packageNum = this.getPackageNum(fbatchPackageId);
        return new BigDecimal(price).divide(packageNum, 8, BigDecimal.ROUND_HALF_UP);
    }

    //获取折扣价格
    private BigDecimal getDiscountPrice(Long fbatchPackageId, Integer foperateType) {
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
            if (null != skuBatchUserPriceResult.getData().getFbatchSellPrice()) {
                price = new BigDecimal(skuBatchUserPriceResult.getData().getFbatchSellPrice()).divide(packageNum, 8, BigDecimal.ROUND_HALF_UP);
            }
        } else {
            return this.getGeneralPrice(fbatchPackageId);
        }
        return price;
    }

    //获取折扣价格最终
    private BigDecimal getDiscountPriceFinal(GoodsDetailMallDto goodsDetailMallDto, Integer foperateType) {
        //sku——user是否支持折扣
        if (null == goodsDetailMallDto.getFskuUserDiscount()) {
            if (this.getIsSkuUserDiscount(goodsDetailMallDto.getFskuId(), foperateType) == 0) {
                return this.getGeneralPrice(goodsDetailMallDto.getFbatchPackageId());
            }
            return this.getDiscountPrice(goodsDetailMallDto.getFbatchPackageId(), foperateType);
        } else {
            if (goodsDetailMallDto.getFskuUserDiscount() == 0) {
                return this.getGeneralPrice(goodsDetailMallDto.getFbatchPackageId());
            }
            return this.getDiscountPrice(goodsDetailMallDto.getFbatchPackageId(), foperateType);
        }
    }

    //是否支持平台会员折扣 0 取 GoodsSkuBatchPrice 1 取 SkuBatchUserPrice
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

    //是否该sku支持该用户类型折扣
    private int getIsSkuUserDiscount(Long fskuId, Integer foperateType) {
        Result<Integer> skuUserDiscountResult = skuUserDiscountConfigApi.countByCriteria(Criteria.of(SkuUserDiscountConfig.class)
                .andEqualTo(SkuUserDiscountConfig::getFskuId, fskuId)
                .andEqualTo(SkuUserDiscountConfig::getFuserTypeId, foperateType.longValue())
                .andEqualTo(SkuUserDiscountConfig::getFisDelete, 0)
                .fields(SkuUserDiscountConfig::getFdiscountId));
        if (!skuUserDiscountResult.isSuccess() || null == skuUserDiscountResult.getData()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        return skuUserDiscountResult.getData().intValue();
    }

    //获取包装规格值
    private BigDecimal getPackageNum(Long fbatchPackageId) {
        Result<SkuBatchPackage> skuBatchPackageResult = skuBatchPackageApi.queryOneByCriteria(Criteria.of(SkuBatchPackage.class)
                .andEqualTo(SkuBatchPackage::getFbatchPackageId, fbatchPackageId)
                .fields(SkuBatchPackage::getFbatchPackageNum));
        if (!skuBatchPackageResult.isSuccess() || null == skuBatchPackageResult.getData()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        return new BigDecimal(skuBatchPackageResult.getData().getFbatchPackageNum());
    }

    //获取到批次的价格
    private GoodsPriceVo getBatchPrice(GoodsDetailMallDto goodsDetailMallDto) {
        //到批次
        Result<List<SkuBatchPackage>> batch = skuBatchPackageApi.queryByCriteria(Criteria.of(SkuBatchPackage.class)
                .andEqualTo(SkuBatchPackage::getFsupplierSkuBatchId, goodsDetailMallDto.getFsupplierSkuBatchId())
                .fields(SkuBatchPackage::getFbatchPackageId));
        if (!batch.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        GoodsPriceVo priceVo = new GoodsPriceVo();
        BigDecimal zero = BigDecimal.ZERO;
        priceVo.setPriceStart(zero);
        priceVo.setPriceEnd(zero);
        priceVo.setTaxStart(zero);
        priceVo.setTaxEnd(zero);
        //sku税率
        BigDecimal skuTaxRate;
        if (null == goodsDetailMallDto.getFskuTaxRate()) {
            SkuDiscountTaxDto isSkuDiscountTax = this.getIsSkuDiscountTax(goodsDetailMallDto.getFskuId());
            skuTaxRate = isSkuDiscountTax.getFskuTaxRate();
        } else {
            skuTaxRate = goodsDetailMallDto.getFskuTaxRate();
        }
        List<SkuBatchPackage> batchResult = batch.getData();
        if (!CollectionUtils.isEmpty(batchResult)) {
            for (int i = 0; i < batchResult.size(); i++) {
                GoodsDetailMallDto param = new GoodsDetailMallDto();
                param.setFuid(goodsDetailMallDto.getFuid());
                param.setFskuId(goodsDetailMallDto.getFskuId());
                param.setFbatchPackageId(batchResult.get(i).getFbatchPackageId());
                param.setFoperateType(goodsDetailMallDto.getFoperateType());
                param.setFverifyStatus(goodsDetailMallDto.getFverifyStatus());
                param.setFskuDiscount(goodsDetailMallDto.getFskuDiscount());
                param.setFskuUserDiscount(goodsDetailMallDto.getFskuUserDiscount());
                param.setFskuTaxRate(goodsDetailMallDto.getFskuTaxRate());
                BigDecimal packagePrice = this.getPackagePrice(param);
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
        skuTaxRate = skuTaxRate.divide(new BigDecimal("10000"), 8, BigDecimal.ROUND_HALF_UP);
        priceVo.setTaxStart(priceVo.getPriceStart().multiply(skuTaxRate));
        priceVo.setTaxEnd(priceVo.getPriceEnd().multiply(skuTaxRate));
        priceVo.setPriceStart(priceVo.getPriceStart().add(priceVo.getTaxStart()));
        priceVo.setPriceEnd(priceVo.getPriceEnd().add(priceVo.getTaxEnd()));
        return priceVo;
    }

    //获取到sku的价格
    private GoodsPriceVo getSkuPrice(GoodsDetailMallDto goodsDetailMallDto) {
        Result<List<SkuBatch>> skuBatche = skuBatchApi.queryByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFskuId, goodsDetailMallDto.getFskuId())
                .andEqualTo(SkuBatch::getFbatchStatus, SkuBatchEnums.Status.OnShelves.getValue())
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
        //sku是否支持打折
        goodsDetailMallDto.setFskuDiscount(this.getIsSkuDiscountTax(goodsDetailMallDto.getFskuId()).getFisUserTypeDiscount());

        //sku_user是否打折
        goodsDetailMallDto.setFskuUserDiscount(this.getIsSkuUserDiscount(goodsDetailMallDto.getFskuId(), goodsDetailMallDto.getFoperateType()));

        //获取sku税率
        BigDecimal skuTaxRate = this.getIsSkuDiscountTax(goodsDetailMallDto.getFskuId()).getFskuTaxRate();
        goodsDetailMallDto.setFskuTaxRate(skuTaxRate);

        List<SkuBatch> skuBatcheResult = skuBatche.getData();
        if (!CollectionUtils.isEmpty(skuBatcheResult)) {
            for (int i = 0; i < skuBatcheResult.size(); i++) {
                GoodsDetailMallDto param = new GoodsDetailMallDto();
                param.setFuid(goodsDetailMallDto.getFuid());
                param.setFskuId(goodsDetailMallDto.getFskuId());
                param.setFsupplierSkuBatchId(skuBatcheResult.get(i).getFsupplierSkuBatchId());
                param.setFoperateType(goodsDetailMallDto.getFoperateType());
                param.setFverifyStatus(goodsDetailMallDto.getFverifyStatus());
                param.setFskuDiscount(goodsDetailMallDto.getFskuDiscount());
                param.setFskuUserDiscount(goodsDetailMallDto.getFskuUserDiscount());
                param.setFskuTaxRate(goodsDetailMallDto.getFskuTaxRate());
                GoodsPriceVo batchPrice = this.getBatchPrice(param);
                if (i == 0) {
                    priceVo.setPriceStart(batchPrice.getPriceStart());
                    priceVo.setPriceEnd(batchPrice.getPriceEnd());
                } else {
                    if (batchPrice.getPriceStart().compareTo(priceVo.getPriceStart()) < 0) {
                        priceVo.setPriceStart(batchPrice.getPriceStart());
                    }
                    if (batchPrice.getPriceEnd().compareTo(priceVo.getPriceEnd()) > 0) {
                        priceVo.setPriceEnd(batchPrice.getPriceEnd());
                    }
                }
            }
        }
        skuTaxRate = skuTaxRate.divide(new BigDecimal("10000"), 8, BigDecimal.ROUND_HALF_UP);
        priceVo.setTaxStart(priceVo.getPriceStart().multiply(skuTaxRate));
        priceVo.setTaxEnd(priceVo.getPriceEnd().multiply(skuTaxRate));
        priceVo.setPriceStart(priceVo.getPriceStart().add(priceVo.getTaxStart()));
        priceVo.setPriceEnd(priceVo.getPriceEnd().add(priceVo.getTaxEnd()));
        return priceVo;
    }

    //获取到spu的价格
    private GoodsPriceVo getSpuPrice(GoodsDetailMallDto goodsDetailMallDto) {
        Result<List<GoodsSku>> listResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFgoodsId, goodsDetailMallDto.getFgoodsId())
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
                GoodsDetailMallDto param = new GoodsDetailMallDto();
                param.setFuid(goodsDetailMallDto.getFuid());
                param.setFskuId(skuResult.get(i).getFskuId());
                param.setFoperateType(goodsDetailMallDto.getFoperateType());
                param.setFverifyStatus(goodsDetailMallDto.getFverifyStatus());
                GoodsPriceVo skuPrice = this.getSkuPrice(param);
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
        //获取库存
        GoodStockSellVo result = new GoodStockSellVo();
        //到批次
        if (null != goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getBatchStock(goodsDetailMallDto);
        }
        //到sku
        if (null != goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getSkuStock(goodsDetailMallDto);
        }
        //到spu
        if (null != goodsDetailMallDto.getFgoodsId() && null == goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getSpuStock(goodsDetailMallDto);
        }
        return Result.success(result);
    }

    //获取批次的库存
    private GoodStockSellVo getBatchStock(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<SkuBatch> batchStockSellResult = skuBatchApi.queryOneByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFsupplierSkuBatchId, goodsDetailMallDto.getFsupplierSkuBatchId())
                .fields(SkuBatch::getFstockRemianNum));
        if (!batchStockSellResult.isSuccess()) {
            logger.info("商品fsupplierSkuBatchId {}获取该批次库存失败", goodsDetailMallDto.getFsupplierSkuBatchId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        SkuBatch batchStockSell = batchStockSellResult.getData();
        if (null != batchStockSell) {
            result.setFstockRemianNum(batchStockSell.getFstockRemianNum());
        }
        return result;
    }

    //获取sku的库存
    private GoodStockSellVo getSkuStock(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<List<SkuBatch>> skuStockSellResult = skuBatchApi.queryByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFskuId, goodsDetailMallDto.getFskuId())
                .andEqualTo(SkuBatch::getFbatchStatus, SkuBatchEnums.Status.OnShelves.getValue())
                .fields(SkuBatch::getFstockRemianNum));
        if (!skuStockSellResult.isSuccess()) {
            logger.info("商品fskuId {}获取该sku库存失败", goodsDetailMallDto.getFskuId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        List<SkuBatch> skuStockSell = skuStockSellResult.getData();
        if (!CollectionUtils.isEmpty(skuStockSell)) {
            long sumSkuStock = skuStockSell.stream().mapToLong(SkuBatch::getFstockRemianNum).sum();
            result.setFstockRemianNum(sumSkuStock);
        }
        return result;
    }

    //获取spu的库存
    private GoodStockSellVo getSpuStock(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<List<SkuBatch>> skuStockSellResult = skuBatchApi.queryByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFgoodsId, goodsDetailMallDto.getFgoodsId())
                .andEqualTo(SkuBatch::getFbatchStatus, SkuBatchEnums.Status.OnShelves.getValue())
                .fields(SkuBatch::getFstockRemianNum));
        if (!skuStockSellResult.isSuccess()) {
            logger.info("商品fgoodsId {}获取该spu库存失败", goodsDetailMallDto.getFgoodsId());
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
        //获取销量
        GoodStockSellVo result = new GoodStockSellVo();
        //到批次
        if (null != goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getBatchSell(goodsDetailMallDto);
        }
        //到sku
        if (null != goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getSkuSell(goodsDetailMallDto);
        }
        //到spu
        if (null != goodsDetailMallDto.getFgoodsId() && null == goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId()) {
            result = this.getSpuSell(goodsDetailMallDto);
        }
        return Result.success(result);
    }

    //获取批次的销量
    private GoodStockSellVo getBatchSell(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<SkuBatch> batchStockSellResult = skuBatchApi.queryOneByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFsupplierSkuBatchId, goodsDetailMallDto.getFsupplierSkuBatchId())
                .fields(SkuBatch::getFsellNum));
        if (!batchStockSellResult.isSuccess()) {
            logger.info("商品fsupplierSkuBatchId {}获取该批次销量失败", goodsDetailMallDto.getFsupplierSkuBatchId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        SkuBatch batchStockSell = batchStockSellResult.getData();
        if (null != batchStockSell) {
            result.setFsellNum(batchStockSell.getFsellNum());
        }
        return result;
    }

    //获取sku的销量
    private GoodStockSellVo getSkuSell(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<GoodsSku> goodsSkuResult = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFskuId, goodsDetailMallDto.getFskuId())
                .fields(GoodsSku::getFsellNum));
        if (!goodsSkuResult.isSuccess()) {
            logger.info("商品fskuId {}获取该sku销量失败", goodsDetailMallDto.getFskuId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null != goodsSkuResult.getData()) {
            result.setFsellNum(goodsSkuResult.getData().getFsellNum());
        }
        return result;
    }

    //获取spu的销量
    private GoodStockSellVo getSpuSell(GoodsDetailMallDto goodsDetailMallDto) {
        GoodStockSellVo result = new GoodStockSellVo();
        Result<List<GoodsSku>> spuStockSellResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFgoodsId, goodsDetailMallDto.getFgoodsId())
                .andEqualTo(GoodsSku::getFskuStatus, GoodsSkuEnums.Status.OnShelves.getValue())
                .andEqualTo(GoodsSku::getFisDelete, "0")
                .fields(GoodsSku::getFsellNum));
        if (!spuStockSellResult.isSuccess()) {
            logger.info("商品fgoodsId {}获取该spu销量失败", goodsDetailMallDto.getFgoodsId());
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
        //是否已经加入常购清单 1是 0否
        Integer fisRegular = 0;
        //查询是否已经加入常购清单
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
        List<CouponVo> allReceiveCoupon = this.getAllReceiveCoupon(fskuId, fuid);
        List<CouponVo> collect = allReceiveCoupon.stream().sorted(Comparator.comparing(CouponVo::getFthresholdAmount).reversed()).limit(3).collect(toList());
        return Result.success(collect);
    }

    @Override
    public Result<GoodsDetailCouponVo> getSkuUserCoupon(Long fskuId, Long fuid) {
        GoodsDetailCouponVo result = new GoodsDetailCouponVo();
        //所有券
        List<CouponVo> allReceiveCoupon = this.getAllReceiveCoupon(fskuId, fuid);
        //已领取券
        List<CouponVo> alreadyReceiveCoupon = this.getAlreadyReceiveCoupon(fskuId, fuid);
        List<Long> alCouponIds = alreadyReceiveCoupon.stream().map(CouponVo::getFcouponId).collect(toList());
        //未领取券
        List<CouponVo> unReceiceCoupon = allReceiveCoupon.stream().filter(item -> !alCouponIds.contains(item.getFcouponId())).collect(toList());


        result.setReceiveCouponLis(alreadyReceiveCoupon.stream().sorted(Comparator.comparing(CouponVo::getFthresholdAmount).reversed()).collect(toList()));
        result.setUnReceiveCouponLis(unReceiceCoupon.stream().sorted(Comparator.comparing(CouponVo::getFthresholdAmount).reversed()).collect(toList()));
        result.setNowDate(new Date());
        return Result.success(result);
    }

    //获取sku满足的所有已领取和未领取的页面领取类型券
    private List<CouponVo> getAllReceiveCoupon(Long fskuId, Long fuid) {
        CouponQueryDto couponQueryDto = new CouponQueryDto();
        couponQueryDto.setReleaseTypes(Lists.newArrayList(CouponReleaseTypeEnum.PAGE_RECEIVE.getCode()));
        couponQueryDto.setSkuId(fskuId);
        couponQueryDto.setUserId(fuid);
        Result<List<CouponQueryVo>> listResult = couponProviderApi.queryBySkuAndUserId(couponQueryDto);
        List<CouponQueryVo> apiCouponLis = listResult.getData();
        List<CouponVo> convert = new ArrayList<>();
        if (!CollectionUtils.isEmpty(apiCouponLis)) {
            convert = dozerHolder.convert(apiCouponLis, CouponVo.class);
        }
        this.dealAmount(convert);
        return convert;
    }

    //获取该sku已领券
    private List<CouponVo> getAlreadyReceiveCoupon(Long fskuId, Long fuid) {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String nowStr = sdf.format(now);
        //查询已经领到的券
        Result<List<CouponReceive>> couponReceResult = couponReceiveApi.queryByCriteria(Criteria.of(CouponReceive.class)
                .andEqualTo(CouponReceive::getFuid, fuid)
                .andEqualTo(CouponReceive::getFuserCouponStatus, CouponReceiveStatusEnum.NOT_USED.getCode())
                .andLessThanOrEqualTo(CouponReceive::getFvalidityStart, nowStr)//时间要转成string
                .andGreaterThanOrEqualTo(CouponReceive::getFvalidityEnd, nowStr)
                .fields(CouponReceive::getFcouponId, CouponReceive::getFvalidityStart, CouponReceive::getFvalidityEnd));
        if (!couponReceResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        List<CouponReceive> couponReceLis = couponReceResult.getData();
        List<CouponVo> result = new ArrayList<>();
        Map<String, Long> skuCondition = new HashMap<>(5);
        if (!CollectionUtils.isEmpty(couponReceLis)) {
            for (CouponReceive couponReceive : couponReceLis) {
                Result<Coupon> couponResult = couponApi.queryOneByCriteria(Criteria.of(Coupon.class)
                        .andEqualTo(Coupon::getFcouponId, couponReceive.getFcouponId())
                        .andEqualTo(Coupon::getFcouponStatus, CouponStatusEnum.PUSHED.getCode())
                        .andEqualTo(Coupon::getFisShow, 1)
                        .fields(Coupon::getFcouponId, Coupon::getFcouponName, Coupon::getFcouponType,
                                Coupon::getFthresholdAmount, Coupon::getFdeductionValue, Coupon::getFvalidityStart,
                                Coupon::getFvalidityEnd, Coupon::getFapplicableSku));
                Coupon coupon = couponResult.getData();
                if (couponResult.isSuccess() && null != coupon) {
                    //1全部商品、2指定商品可用、3指定商品不可用
                    int ableSku = coupon.getFapplicableSku().intValue();
                    if (ableSku == 1) {
                        result.add(dozerMapper.map(coupon, CouponVo.class));
                    } else if (ableSku == 2) {
                        //如果是单独存的skuid
                        Result<Integer> countCouponSku = this.getCouponSkuCount(couponReceive.getFcouponId(), fskuId);
                        if (countCouponSku.isSuccess() && countCouponSku.getData() > 0) {
                            result.add(dozerMapper.map(coupon, CouponVo.class));
                            continue;
                        }
                        //如果存的是条件
                        Result<CouponApplicableSkuCondition> skuConditionRes = this.getCouponSkuAble(couponReceive.getFcouponId());
                        if (!skuConditionRes.isSuccess()) {
                            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                        }
                        CouponApplicableSkuCondition couponCondition = skuConditionRes.getData();

                        this.setSkuCondition(skuCondition, fskuId);
                        if (null == couponCondition) {
                            continue;
                        }
                        //优惠券条件
                        List<Long> fbrandIds_coupon = (List<Long>) JSON.parse(couponCondition.getFbrandId());
                        Map<Integer, List<Long>> fcategoryIds_coupon = (Map<Integer, List<Long>>) JSON.parse(couponCondition.getFcategoryId());
                        List<Long> flabelIds_coupon = (List<Long>) JSON.parse(couponCondition.getFlabelId());

                        //skuid反推的条件
                        Long brandId = skuCondition.get("brandId");
                        Long categoryId1 = skuCondition.get("categoryId1");
                        Long categoryId2 = skuCondition.get("categoryId2");
                        Long categoryId3 = skuCondition.get("categoryId3");
                        Long labelId = skuCondition.get("labelId");

                        if (fbrandIds_coupon.contains(brandId)) {
                            result.add(dozerMapper.map(coupon, CouponVo.class));
                            continue;
                        }
                        if (null != fcategoryIds_coupon.get(1) && fcategoryIds_coupon.get(1).contains(categoryId1)) {
                            result.add(dozerMapper.map(coupon, CouponVo.class));
                            continue;
                        }
                        if (null != fcategoryIds_coupon.get(2) && fcategoryIds_coupon.get(1).contains(categoryId2)) {
                            result.add(dozerMapper.map(coupon, CouponVo.class));
                            continue;
                        }
                        if (null != fcategoryIds_coupon.get(3) && fcategoryIds_coupon.get(1).contains(categoryId3)) {
                            result.add(dozerMapper.map(coupon, CouponVo.class));
                            continue;
                        }
                        if (flabelIds_coupon.contains(labelId)) {
                            result.add(dozerMapper.map(coupon, CouponVo.class));
                            continue;
                        }
                    } else {
                        //指定商品不可用--sku和条件都不满足才可以
                        //如果是单独存的skuid
                        Result<Integer> countCouponSku = this.getCouponSkuCount(couponReceive.getFcouponId(), fskuId);
                        if (countCouponSku.isSuccess() && countCouponSku.getData() == 0) {
                            //如果存的是条件
                            Result<CouponApplicableSkuCondition> skuConditionRes = this.getCouponSkuAble(couponReceive.getFcouponId());
                            if (!skuConditionRes.isSuccess()) {
                                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                            }
                            CouponApplicableSkuCondition couponCondition = skuConditionRes.getData();
                            this.setSkuCondition(skuCondition, fskuId);
                            if (null == couponCondition) {
                                //优惠券条件
                                List<Long> fbrandIds_coupon = (List<Long>) JSON.parse(couponCondition.getFbrandId());
                                Map<Integer, List<Long>> fcategoryIds_coupon = (Map<Integer, List<Long>>) JSON.parse(couponCondition.getFcategoryId());
                                List<Long> flabelIds_coupon = (List<Long>) JSON.parse(couponCondition.getFlabelId());

                                //skuid反推的条件
                                Long brandId = skuCondition.get("brandId");
                                Long categoryId1 = skuCondition.get("categoryId1");
                                Long categoryId2 = skuCondition.get("categoryId2");
                                Long categoryId3 = skuCondition.get("categoryId3");
                                Long labelId = skuCondition.get("labelId");

                                if (!fbrandIds_coupon.contains(brandId)) {
                                    if (null != fcategoryIds_coupon.get(1) && fcategoryIds_coupon.get(1).contains(categoryId1)) {
                                        if (null != fcategoryIds_coupon.get(2) && fcategoryIds_coupon.get(1).contains(categoryId2)) {
                                            if (null != fcategoryIds_coupon.get(3) && fcategoryIds_coupon.get(1).contains(categoryId3)) {
                                                if (flabelIds_coupon.contains(labelId)) {
                                                    result.add(dozerMapper.map(coupon, CouponVo.class));
                                                    continue;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        this.dealAmount(result);
        return result;
    }

    private Result<Integer> getCouponSkuCount(Long fcouponId, Long fskuId) {
        Result<Integer> countCouponSku = couponApplicableSkuApi.countByCriteria(Criteria.of(CouponApplicableSku.class)
                .andEqualTo(CouponApplicableSku::getFcouponId, fcouponId)
                .andEqualTo(CouponApplicableSku::getFskuId, fskuId));
        return countCouponSku;
    }

    private Result<CouponApplicableSkuCondition> getCouponSkuAble(Long fcouponId) {
        Result<CouponApplicableSkuCondition> skuConditionRes = couponApplicableSkuConditionApi.queryOneByCriteria(Criteria.of(CouponApplicableSkuCondition.class)
                .andEqualTo(CouponApplicableSkuCondition::getFcouponId, fcouponId)
                .fields(CouponApplicableSkuCondition::getFbrandId,
                        CouponApplicableSkuCondition::getFcategoryId,
                        CouponApplicableSkuCondition::getFlabelId));
        return skuConditionRes;
    }

    private Map<String, Long> setSkuCondition(Map<String, Long> skuCondition, Long fskuId) {
        if (null == skuCondition.get("brandId")) {
            Result<GoodsSku> goodsSkuResult = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                    .andEqualTo(GoodsSku::getFskuId, fskuId)
                    .fields(GoodsSku::getFbrandId, GoodsSku::getFcategoryId1, GoodsSku::getFcategoryId2, GoodsSku::getFcategoryId3, GoodsSku::getFlabelId));
            GoodsSku goodsSku = goodsSkuResult.getData();
            if (goodsSkuResult.isSuccess() && null != goodsSku) {
                skuCondition.put("brandId", goodsSku.getFbrandId());
                skuCondition.put("categoryId1", goodsSku.getFcategoryId1());
                skuCondition.put("categoryId2", goodsSku.getFcategoryId2());
                skuCondition.put("categoryId3", goodsSku.getFcategoryId3());
                skuCondition.put("labelId", goodsSku.getFlabelId());
            }
        }
        return skuCondition;
    }

    private List<CouponVo> dealAmount(List<CouponVo> result) {
        for (CouponVo couponMap : result) {
            couponMap.setFthresholdAmount(PriceUtil.toYuan(couponMap.getFthresholdAmount()));
            //优惠券类型，1满减券、2折扣券
            if (couponMap.getFcouponType().equals(CouponTypeEnum.FULL_REDUCTION.getCode())) {
                couponMap.setFdeductionValue(PriceUtil.toYuan(couponMap.getFdeductionValue()));
            } else {
                couponMap.setFdeductionValue(couponMap.getFdeductionValue().divide(new BigDecimal("10"), 1, BigDecimal.ROUND_HALF_UP));
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
        //1全部商品、2指定商品可用、3指定商品不可用
        int fapplicableSku = couponResult.getData().getFapplicableSku().intValue();
        if (1 == fapplicableSku) {
            result = "全部商品可用";
        } else if (2 == fapplicableSku) {
            if (this.isHasCouponSkuCondition(fcouponId)) {
                result = "部分商品可用" + "\n" + this.getCouponSkuCondition(fcouponId);
            } else {
                result = "部分商品可用";
            }
        } else {
            if (this.isHasCouponSkuCondition(fcouponId)) {
                result = "部分商品不可用" + "\n" + this.getCouponSkuCondition(fcouponId);
            } else {
                result = "部分商品不可用";
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

    private String getCouponSkuCondition(Long fcouponId) {
        String result = "";
        //贸易类型--三级分类--品牌
        Result<CouponApplicableSkuCondition> conditionResult = couponApplicableSkuConditionApi.queryOneByCriteria(Criteria.of(CouponApplicableSkuCondition.class)
                .andEqualTo(CouponApplicableSkuCondition::getFcouponId, fcouponId)
                .fields(CouponApplicableSkuCondition::getFbrandName,
                        CouponApplicableSkuCondition::getFcategoryName,
                        CouponApplicableSkuCondition::getFtradeName));
        CouponApplicableSkuCondition conditionData = conditionResult.getData();
        if (!conditionResult.isSuccess() || null == conditionData) {
            return result;
        }
        StringBuffer resBf = new StringBuffer();
        if (!StringUtils.isEmpty(conditionData.getFtradeName())) {
            resBf.append("贸易类型：").append(conditionData.getFtradeName()).append("\n");
        }
        if (!StringUtils.isEmpty(conditionData.getFcategoryName())) {
            resBf.append("品类：").append(conditionData.getFcategoryName()).append("\n");
        }
        if (!StringUtils.isEmpty(conditionData.getFbrandName())) {
            resBf.append("品牌：").append(conditionData.getFbrandName()).append("\n");
        }
        result = resBf.toString();
        return result;
    }

    @Override
    public Result addReceiveCoupon(Long fcouponId, Long fuid) {
        if (null == fcouponId || null == fuid) {
            return Result.failure(MallPcExceptionCode.PARAM_ERROR);
        }
        //查询优惠券--状态（已发布）--类型（页面领取）--剩余数量--有效期结束时间--发放结束时间
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
        //查询已经领到的券张数
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
        String fcouponCode = receiveCouponDto.getFcouponCode();
        if (null == fcouponId || null == fuid) {
            return Result.failure(MallPcExceptionCode.PARAM_ERROR);
        }
        String lockKey = StringUtils.join(Lists.newArrayList(MallPcConstants.MALL_RECEIVE_COUPON, fcouponId, fuid), ":");
        if (null != fcouponCode) {
            lockKey = StringUtils.join(Lists.newArrayList(MallPcConstants.MALL_RECEIVE_COUPON, fcouponId, fuid, fcouponCode), ":");
        }
        String lockValue = RandomUtils.getUUID();
        try {
            //绑定用户和优惠券关系
            Ensure.that(xybbcLock.tryLockTimes(lockKey, lockValue, 3, 6)).isTrue(MallPcExceptionCode.SYSTEM_BUSY_ERROR);
            CouponBindUser couponBindUser = new CouponBindUser();
            couponBindUser.setFcouponId(fcouponId);
            couponBindUser.setFuid(fuid);
            couponBindUser.setFcreateTime(new Date());
            couponBindUser.setFisReceived(1);
            Result<Integer> insertBindResult = couponBindUserApi.create(couponBindUser);
            if (!insertBindResult.isSuccess()) {
                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
            }
            //更新优惠券发放数量
            CouponReleaseDto couponReleaseDto = new CouponReleaseDto();
            couponReleaseDto.setCouponScene(CouponScene.PAGE_RECEIVE);
            couponReleaseDto.setCouponId(fcouponId);
            couponReleaseDto.setUserId(fuid);
            couponReleaseDto.setCouponCode(fcouponCode);
            couponReleaseDto.setAlreadyReceived(true);
            couponReleaseDto.setDeltaValue(-1);
            Result updateReleaseResult = couponProviderApi.updateReleaseQty(couponReleaseDto);
            if (!updateReleaseResult.isSuccess()) {
                return updateReleaseResult;
            }
            //调用领券服务
            Result receiveReceive = couponProviderApi.receive(couponReleaseDto);
            if (!receiveReceive.isSuccess()) {
                return receiveReceive;
            }
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

}