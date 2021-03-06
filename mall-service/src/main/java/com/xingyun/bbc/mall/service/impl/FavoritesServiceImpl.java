package com.xingyun.bbc.mall.service.impl;

import com.xingyun.bbc.core.enums.ResultStatus;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.query.Criteria;
import com.xingyun.bbc.core.sku.api.*;
import com.xingyun.bbc.core.sku.enums.GoodsSkuEnums;
import com.xingyun.bbc.core.sku.enums.SkuBatchEnums;
import com.xingyun.bbc.core.sku.po.*;
import com.xingyun.bbc.core.user.api.UserApi;
import com.xingyun.bbc.core.user.api.UserDeliveryApi;
import com.xingyun.bbc.core.user.enums.UserVerifyStatusEnum;
import com.xingyun.bbc.core.user.po.User;
import com.xingyun.bbc.core.user.po.UserDelivery;
import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.mall.base.utils.PriceUtil;
import com.xingyun.bbc.mall.common.constans.MallConstants;
import com.xingyun.bbc.mall.common.ensure.Ensure;
import com.xingyun.bbc.mall.common.exception.MallExceptionCode;
import com.xingyun.bbc.mall.model.dto.GoodsPriceIntervalDto;
import com.xingyun.bbc.mall.model.dto.SkuDiscountTaxDto;
import com.xingyun.bbc.mall.model.vo.GoodsPriceIntervalVo;
import com.xingyun.bbc.mall.service.FavoritesService;
import com.xingyun.bbc.order.api.FavoritesApi;
import com.xingyun.bbc.order.model.dto.favorites.FavoritesDto;
import com.xingyun.bbc.order.model.vo.PageVo;
import com.xingyun.bbc.order.model.vo.favorites.FavoritesVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.dozer.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class FavoritesServiceImpl implements FavoritesService {

    @Autowired
    private UserApi userApi;

    @Autowired
    private GoodsSkuApi goodsSkuApi;

    @Autowired
    private SkuBatchApi skuBatchApi;

    @Autowired
    private SkuBatchPackageApi skuBatchPackageApi;

    @Autowired
    private GoodsSkuBatchPriceApi goodsSkuBatchPriceApi;

    @Autowired
    private SkuBatchUserPriceApi skuBatchUserPriceApi;

    @Autowired
    private SkuUserDiscountConfigApi skuUserDiscountConfigApi;

    @Autowired
    private Mapper dozerMapper;

    @Autowired
    private FavoritesApi favoritesApi;

    @Override
    public GoodsPriceIntervalVo queryGoodPriceInterval(GoodsPriceIntervalDto goodsDetailMallDto) {
        //token????????????????????????--??????????????????
        if (!goodsDetailMallDto.getFverifyStatus().equals(UserVerifyStatusEnum.AUTHENTICATED.getCode())) {
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
        GoodsPriceIntervalVo priceResult = new GoodsPriceIntervalVo();
        //?????????
        if (null != goodsDetailMallDto.getFbatchPackageId()) {
            GoodsPriceIntervalDto param = dozerMapper.map(goodsDetailMallDto, GoodsPriceIntervalDto.class);
            BigDecimal packagePrice = this.getPackagePrice(goodsDetailMallDto, param);
//            priceResult.setRealPrice(PriceUtil.toYuan(packagePrice));
            priceResult.setPriceStart(PriceUtil.toYuan(packagePrice));
        }
        //?????????
        if (null != goodsDetailMallDto.getFsupplierSkuBatchId() && null == goodsDetailMallDto.getFbatchPackageId()) {
            GoodsPriceIntervalDto param = dozerMapper.map(goodsDetailMallDto, GoodsPriceIntervalDto.class);
            priceResult = this.getBatchPrice(goodsDetailMallDto, param);
            this.dealGoodDetailPriceToYuan(priceResult);
        }
        //???sku
        if (null != goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId() && null == goodsDetailMallDto.getFbatchPackageId()) {
            GoodsPriceIntervalDto param = dozerMapper.map(goodsDetailMallDto, GoodsPriceIntervalDto.class);
            priceResult = this.getSkuPrice(goodsDetailMallDto, param);
            this.dealGoodDetailPriceToYuan(priceResult);
        }
        //???spu
        if (null != goodsDetailMallDto.getFgoodsId() && null == goodsDetailMallDto.getFskuId() && null == goodsDetailMallDto.getFsupplierSkuBatchId() && null == goodsDetailMallDto.getFbatchPackageId()) {
            GoodsPriceIntervalDto param = dozerMapper.map(goodsDetailMallDto, GoodsPriceIntervalDto.class);
            priceResult = this.getSpuPrice(goodsDetailMallDto, param);
            this.dealGoodDetailPriceToYuan(priceResult);
        }

        /**
        //??????????????? ?????????????????????PriceStart???????????????????????????????????????
        if (null == priceResult.getPriceEnd()) {
            log.info("---------------------1??????????????????????????????---------------------");
            //???????????????????????? 1.???????????? 2.??????????????? 3.??????????????? 4.??????????????????
            Result<SkuBatch> skuBatchResult = skuBatchApi.queryOneByCriteria(Criteria.of(SkuBatch.class)
                    .andEqualTo(SkuBatch::getFsupplierSkuBatchId, goodsDetailMallDto.getFsupplierSkuBatchId())
                    .fields(SkuBatch::getFbatchPriceType, SkuBatch::getFfreightId, SkuBatch::getFsupplierSkuBatchId));
            SkuBatch fskuBatch = skuBatchResult.getData();
            Ensure.that(skuBatchResult.isSuccess()).isTrue(new MallExceptionCode(skuBatchResult.getCode(), skuBatchResult.getMsg()));
            Integer fbatchPriceType = fskuBatch.getFbatchPriceType();
            //????????????????????????---????????????????????????
            //????????????
            Result<GoodsSku> skuTaxResult = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                    .andEqualTo(GoodsSku::getFskuId, goodsDetailMallDto.getFskuId())
                    .fields(GoodsSku::getFskuTaxRate));
            Ensure.that(skuTaxResult.isSuccess()).isTrue(new MallExceptionCode(skuTaxResult.getCode(), skuTaxResult.getMsg()));
            Long fskuTaxRate = 0l;
            fskuTaxRate = skuTaxResult.getData().getFskuTaxRate();
            //(?????????*????????????)
            BigDecimal orgPrice = priceResult.getPriceStart().multiply(new BigDecimal(goodsDetailMallDto.getFnum()));
            //?????? = (?????????*????????????) * ??????
            BigDecimal taxPrice = BigDecimal.ZERO;

            if (fbatchPriceType.intValue() == 2 || fbatchPriceType.intValue() == 4) {
                taxPrice = orgPrice.multiply(new BigDecimal(fskuTaxRate))
                        .divide(MallConstants.TEN_THOUSAND, 2, BigDecimal.ROUND_HALF_UP);
            }
            //?????? = (?????????*????????????) + ??????
            BigDecimal priceTotal = orgPrice.add(taxPrice);
//            //???????????? = ?????? / ?????? /??????????????????
//            BigDecimal dealUnitPrice = BigDecimal.ZERO;
//            if (null != goodsDetailMallDto.getFbatchPackageNum()) {
//                dealUnitPrice = priceTotal.divide(new BigDecimal(goodsDetailMallDto.getFnum()).multiply(new BigDecimal(goodsDetailMallDto.getFbatchPackageNum())), 2, BigDecimal.ROUND_HALF_UP);
//            }

            priceResult.setPriceStart(priceTotal);
            priceResult.setTaxPrice(taxPrice);
//            priceResult.setDealUnitPrice(dealUnitPrice);
            log.info("---------------------2??????????????????????????????---------------------");
        }

         **/
        return priceResult;
    }

    private void dealGoodDetailPriceToYuan(GoodsPriceIntervalVo goodsPriceVo) {
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
    private BigDecimal getPackagePrice(GoodsPriceIntervalDto goodsDetailMallDto, GoodsPriceIntervalDto param) {
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
    private BigDecimal getGeneralPrice(GoodsPriceIntervalDto goodsDetailMallDto, GoodsPriceIntervalDto param) {
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
    private BigDecimal getDiscountPrice(GoodsPriceIntervalDto goodsDetailMallDto, GoodsPriceIntervalDto param) {
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
    private int getIsSkuUserDiscount(GoodsPriceIntervalDto goodsDetailMallDto, Integer foperateType) {
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
    private GoodsPriceIntervalVo getBatchPrice(GoodsPriceIntervalDto goodsDetailMallDto, GoodsPriceIntervalDto param) {
        //?????????
        Result<List<SkuBatchPackage>> batchPackageResult = skuBatchPackageApi.queryByCriteria(Criteria.of(SkuBatchPackage.class)
                .andEqualTo(SkuBatchPackage::getFsupplierSkuBatchId, param.getFsupplierSkuBatchId())
                .fields(SkuBatchPackage::getFbatchPackageId));
        if (!batchPackageResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        GoodsPriceIntervalVo priceVo = new GoodsPriceIntervalVo();
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
        Ensure.that(skuBatchResult.isSuccess()).isTrue(new MallExceptionCode(skuBatchResult.getCode(), skuBatchResult.getMsg()));
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
    private GoodsPriceIntervalVo getSkuPrice(GoodsPriceIntervalDto goodsDetailMallDto, GoodsPriceIntervalDto param) {
        Result<List<SkuBatch>> skuBatche = skuBatchApi.queryByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFskuId, param.getFskuId())
                .andEqualTo(SkuBatch::getFbatchStatus, SkuBatchEnums.Status.OnShelves.getValue())
                .andEqualTo(SkuBatch::getFbatchPutwaySort,1)
                .fields(SkuBatch::getFsupplierSkuBatchId));
        if (!skuBatche.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        GoodsPriceIntervalVo priceVo = new GoodsPriceIntervalVo();
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
                GoodsPriceIntervalVo batchPrice = this.getBatchPrice(goodsDetailMallDto, param);
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
    private GoodsPriceIntervalVo getSpuPrice(GoodsPriceIntervalDto goodsDetailMallDto, GoodsPriceIntervalDto param) {
        Result<List<GoodsSku>> listResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFgoodsId, param.getFgoodsId())
                .andEqualTo(GoodsSku::getFskuStatus, GoodsSkuEnums.Status.OnShelves.getValue())
                .andEqualTo(GoodsSku::getFisDelete, "0")
                .fields(GoodsSku::getFskuId));
        if (!listResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        GoodsPriceIntervalVo priceVo = new GoodsPriceIntervalVo();
        BigDecimal zero = BigDecimal.ZERO;
        priceVo.setPriceStart(zero);
        priceVo.setPriceEnd(zero);
        priceVo.setTaxStart(zero);
        priceVo.setTaxEnd(zero);
        List<GoodsSku> skuResult = listResult.getData();
        if (!CollectionUtils.isEmpty(skuResult)) {
            for (int i = 0; i < skuResult.size(); i++) {
                param.setFskuId(skuResult.get(i).getFskuId());
                GoodsPriceIntervalVo skuPrice = this.getSkuPrice(goodsDetailMallDto, param);
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
    public PageVo<FavoritesVo> queryFavoritesPage(GoodsPriceIntervalDto dto) {
        FavoritesDto favoritesDto = new FavoritesDto();
        favoritesDto.setFuid(dto.getFuid());
        favoritesDto.setPageNum(dto.getPageNum());
        favoritesDto.setPageSize(dto.getPageSize());
        favoritesDto.setFcategoryId(dto.getFcategoryId());
        Result<PageVo<FavoritesVo>> pageVoResult = favoritesApi.favoritesList(favoritesDto);

        Ensure.that(pageVoResult.isSuccess()).isTrue(MallExceptionCode.SYSTEM_ERROR);
        PageVo<FavoritesVo> data = pageVoResult.getData();
        List<FavoritesVo> favoritesVos = data.getList();
        if (CollectionUtils.isEmpty(favoritesVos)) {
            return data;
        }

        favoritesVos.forEach(fa->{
            GoodsPriceIntervalDto intervalDto = new GoodsPriceIntervalDto();
            intervalDto.setFuid(dto.getFuid())
                    .setFverifyStatus(dto.getFverifyStatus())
                    .setFoperateType(dto.getFoperateType())
                    .setFgoodsId(fa.getFgoodsId());
            GoodsPriceIntervalVo goodsPriceIntervalVo = queryGoodPriceInterval(intervalDto);
            if (Objects.nonNull(goodsPriceIntervalVo)){
                fa.setMinPrice(goodsPriceIntervalVo.getPriceStart());
                fa.setMaxPrice(goodsPriceIntervalVo.getPriceEnd());
                if (goodsPriceIntervalVo.getPriceStart().compareTo(BigDecimal.ZERO)<=0){
                    fa.setMinPrice(goodsPriceIntervalVo.getPriceEnd());
                }
            }
        });
        data.setList(favoritesVos);
        return data;
    }
}
