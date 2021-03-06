package com.xingyun.bbc.mall.service.impl;


import com.google.common.collect.Lists;
import com.xingyun.bbc.common.redis.XyRedisManager;
import com.xingyun.bbc.core.enums.ResultStatus;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.operate.api.GuidePageApi;
import com.xingyun.bbc.core.operate.api.PageConfigApi;
import com.xingyun.bbc.core.operate.po.GuidePage;
import com.xingyun.bbc.core.operate.po.PageConfig;
import com.xingyun.bbc.core.query.Criteria;

import com.xingyun.bbc.core.sku.api.*;
import com.xingyun.bbc.core.sku.po.*;

import com.xingyun.bbc.core.user.api.UserApi;
import com.xingyun.bbc.core.user.po.User;
import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.mall.base.utils.DozerHolder;
import com.xingyun.bbc.mall.base.utils.JacksonUtils;
import com.xingyun.bbc.mall.base.utils.PageUtils;
import com.xingyun.bbc.mall.common.constans.GuidePageContants;

import com.xingyun.bbc.mall.common.constans.MallConstants;
import com.xingyun.bbc.mall.common.constans.PageConfigContants;

import com.xingyun.bbc.mall.common.exception.MallExceptionCode;
import com.xingyun.bbc.mall.model.dto.CategoryDto;

import com.xingyun.bbc.mall.model.dto.SearchItemDto;
import com.xingyun.bbc.mall.model.vo.*;
import com.xingyun.bbc.mall.service.IndexService;

import org.apache.commons.collections.CollectionUtils;
import org.dozer.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author lll
 * @Title:
 * @Description:
 * @date 2019-09-03 11:00
 */
@Service
public class IndexServiceImpl implements IndexService {
    
    public static final Logger logger = LoggerFactory.getLogger(IndexServiceImpl.class);
    
    @Autowired
    private PageConfigApi pageConfigApi;
    @Autowired
    private GuidePageApi guidePageApi;
    @Resource
    private DozerHolder holder;
    @Autowired
    private XyRedisManager xyRedisManager;
    
    @Autowired
    GoodsCategoryApi goodsCategoryApi;
    
    @Autowired
    private GoodsSkuApi goodsSkuApi;
    
    @Autowired
    private Mapper dozerMapper;
    
    @Autowired
    GoodsThumbImageApi goodsThumbImageApi;
    
    @Autowired
    SkuBatchApi skuBatchApi;
    
    @Autowired
    UserApi userApi;
    
    @Autowired
    GoodsSkuBatchPriceApi goodsSkuBatchPriceApi;
    
    @Autowired
    SkuBatchUserPriceApi skuBatchUserPriceApi;
    
    @Autowired
    private SkuUserDiscountConfigApi skuUserDiscountConfigApi;
    
    @Autowired
    private PageUtils pageUtils;
    
    @Autowired
    private DozerHolder dozerHolder;
    
    @Autowired
    private SkuBatchPackageApi skuBatchPackageApi;
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ??????????????????
     * @Param: [fposition]
     * @return: Result<List   <   PageConfigVo>>
     * @date 2019/9/20 13:49
     */
    @Override
    public Result<List<PageConfigVo>> getConfig(Integer position) {
        if (null == position) {
            throw new BizException(MallExceptionCode.REQUIRED_PARAM_MISSING);
        }
        List<PageConfigVo> pageConfigRedisResultList = this.selectPageConfigRedisList(position);
        //?????????pageConfigRedis key??????????????????,??????????????????????????????
        if (CollectionUtils.isEmpty(pageConfigRedisResultList)) {
            Criteria<PageConfig, Object> pageConfigCriteria = Criteria.of(PageConfig.class);
            pageConfigCriteria.andEqualTo(PageConfig::getFposition, position)
            .fields(PageConfig::getFcategoryLevel,PageConfig::getFconfigName
                    ,PageConfig::getFimgUrl,PageConfig::getFlocation,PageConfig::getFpeiodEndTime
                    ,PageConfig::getFperiodStartTime,PageConfig::getFposition
                    ,PageConfig::getFredirectUrl,PageConfig::getFrelationId
                    ,PageConfig::getFsortValue,PageConfig::getFtype
                    ,PageConfig::getFviewType,PageConfig::getFisDelete);
            //???????????????,???????????????0?????????
            pageConfigCriteria.andEqualTo(PageConfig::getFisDelete, 0).andEqualTo(PageConfig::getFconfigType, 0);
            //?????????0:Banner?????? 1:ICON????????????sortValue????????????????????????
            if (position == 0 || position == 1) {
                pageConfigCriteria.sort(PageConfig::getFsortValue);
            } else {
                //?????????1???????????????????????????????????????location????????????
                pageConfigCriteria.sort(PageConfig::getFlocation);
            }
            Result<List<PageConfig>> pageConfigResult = pageConfigApi.queryByCriteria(pageConfigCriteria);
            if (!pageConfigResult.isSuccess()) {
                logger.error("???????????????????????????????????????????????????{}", position);
                throw new BizException(ResultStatus.INTERNAL_SERVER_ERROR);
            }
            List<PageConfig> pageConfigs = pageConfigResult.getData();
            pageConfigRedisResultList = holder.convert(pageConfigs, PageConfigVo.class);
            //?????????2????????????????????????????????????
            return this.getByTime(pageConfigRedisResultList, position);
        } else {
            return this.getByTime(pageConfigRedisResultList, position);
        }
    }
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ??????????????????????????????????????????
     * @Param: [fposition]
     * @return: Result<ListPageConfigVo>>
     * @date 2019/9/20 13:49
     */
    private Result<List<PageConfigVo>> getByTime(List<PageConfigVo> pageConfigRedisResultList, Integer position) {
        //?????????????????????2???????????????????????????????????????
        if (position == 2) {
            List<PageConfigVo> pageConfigVos = new ArrayList<>();
            Date currentTime = new Date();
            for (PageConfigVo pageConfigVo : pageConfigRedisResultList) {
                Integer viewType = pageConfigVo.getFviewType();
                Date startTime = pageConfigVo.getFperiodStartTime();
                Date endTime = pageConfigVo.getFpeiodEndTime();
                //????????????????????????????????????
                if (viewType == 0) {
                    pageConfigVos.add(pageConfigVo);
                } else {
                    //??????????????????????????????????????????
                    if (startTime.getTime() <= currentTime.getTime() && endTime.getTime() >= currentTime.getTime()) {
                        pageConfigVos.add(pageConfigVo);
                    }
                }
            }
            return Result.success(pageConfigVos);
        } else {
            return Result.success(pageConfigRedisResultList);
        }
    }
    
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ??????redis??????????????????
     * @Param: [fposition]
     * @return: Result<List   <   PageConfigVo>>
     * @date 2019/9/20 13:49
     */
    private List<PageConfigVo> selectPageConfigRedisList(int position) {
        try {
            logger.info("??????redisKey,position:{}", position);
            List<PageConfigVo> pageTempList = Lists.newArrayList();
            //??????key
            String redisKey = PageConfigContants.PAGE_CONFIG;
            List<Object> result = xyRedisManager.hValues(redisKey);
            if (CollectionUtils.isNotEmpty(result)) {
                //????????????
                List<PageConfig> pageConfigs = holder.convert(result, PageConfig.class);
                List<PageConfigVo> pageConfigVos = holder.convert(pageConfigs, PageConfigVo.class);
                pageTempList = pageConfigVos.stream().filter(index -> {
                    //??????????????????????????????
                    if (index.getFisDelete() != null) {
                        boolean a = index.getFisDelete() == 0;
                        boolean b = index.getFposition() == position;
                        boolean c = a && b;
                        return c;
                    } else {
                        boolean b = index.getFposition() == position;
                        return b;
                    }
                }).collect(Collectors.toList());
                //?????????0:Banner?????? 1:ICON????????????sortValue????????????????????????
                if (position == 0 || position == 1) {
                    Collections.sort(pageTempList, new Comparator<PageConfigVo>() {
                        @Override
                        public int compare(PageConfigVo o1, PageConfigVo o2) {
                            return o1.getFsortValue().compareTo(o2.getFsortValue());
                        }
                    });
                } else {
                    //?????????1???????????????????????????????????????location????????????
                    Collections.sort(pageTempList, new Comparator<PageConfigVo>() {
                        @Override
                        public int compare(PageConfigVo o1, PageConfigVo o2) {
                            return o1.getFlocation().compareTo(o2.getFlocation());
                        }
                    });
                }
            }
            return pageTempList;
        } catch (Exception e) {
            throw new BizException(ResultStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ??????????????????
     * @Param: [userId]
     * @return: Result<User><PageConfigVo>>
     * @date 2019/9/20 13:49
     */
    Result<User> getUser(Integer userId) {
        Result<User> userResult = userApi.queryById(userId);
        if (!userResult.isSuccess()) {
            logger.error("???????????????????????? ??????id{}", userId);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (Objects.isNull(userResult.getData())) {
            throw new BizException(MallExceptionCode.NO_USER);
        }
        return Result.success(userResult.getData());
    }
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ???????????????sku??????
     * @Param: [userId]
     * @return: Result<User><PageConfigVo>>
     * @date 2019/9/20 13:49
     */
    Result<List<GoodsSku>> getSkuList(CategoryDto categoryDto, SearchItemDto searchItemDto) {
        Criteria<GoodsSku, Object> criteria = Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFisDelete, 0)
                .andEqualTo(GoodsSku::getFcategoryId1, categoryDto.getFcategoryId1())
                .andEqualTo(GoodsSku::getFskuStatus, 1);
        //??????sku????????????
        Result<List<GoodsSku>> result = goodsSkuApi.queryByCriteria(
                criteria.fields(
                        GoodsSku::getFskuName, GoodsSku::getFskuCode,
                        GoodsSku::getFgoodsId, GoodsSku::getFskuId,
                        GoodsSku::getFskuThumbImage, GoodsSku::getFskuTaxRate,
                        GoodsSku::getFisUserTypeDiscount)
                        .page(categoryDto.getCurrentPage(), categoryDto.getPageSize())
        );
        if (!result.isSuccess()) {
            logger.error("?????????????????????????????? ?????????{}", searchItemDto.getPageIndex());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        return Result.success(result.getData());
    }
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ???????????????sku??????
     * @Param: [userId]
     * @return: Result<User><PageConfigVo>>
     * @date 2019/9/20 13:49
     */
    Result<Integer> getSkuCount(CategoryDto categoryDto) {
        Criteria<GoodsSku, Object> criteria = Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFisDelete, 0)
                .andEqualTo(GoodsSku::getFcategoryId1, categoryDto.getFcategoryId1())
                .andEqualTo(GoodsSku::getFskuStatus, 1);
        //??????sku????????????
        Result<Integer> totalResult = goodsSkuApi.countByCriteria(criteria);
        if (!totalResult.isSuccess()) {
            logger.error("????????????????????????????????????");
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        return Result.success(totalResult.getData());
    }
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ?????????????????????????????????
     * @Param: [userId]
     * @return: Result<User><PageConfigVo>>
     * @date 2019/9/20 13:49
     */
    Result<List<SkuBatch>> getSkuBatchList(List<Long> skuIdList, SearchItemDto searchItemDto) {
        Criteria<SkuBatch, Object> skuBatchCriteria = Criteria.of(SkuBatch.class)
                .andIn(SkuBatch::getFskuId, skuIdList);
        Result<List<SkuBatch>> skuBatchList = skuBatchApi.queryByCriteria(skuBatchCriteria.fields(SkuBatch::getFskuId, SkuBatch::getFsellNum));
        if (!skuBatchList.isSuccess()) {
            logger.error("???????????????????????????????????? ????????????{}", searchItemDto.getPageIndex());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        return Result.success(skuBatchList.getData());
    }
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ???????????????????????????????????????????????????????????????
     * @Param: [userId]
     * @return: Result<User><PageConfigVo>>
     * @date 2019/9/20 13:49
     */
    Result<List<SkuBatch>> getSkuBatchListByStatu(List<Long> skuIdList, SearchItemDto searchItemDto) {
        Criteria<SkuBatch, Object> batchCriteria = Criteria.of(SkuBatch.class).fields(
                SkuBatch::getFsupplierSkuBatchId,
                SkuBatch::getFskuId,
                SkuBatch::getFbatchPriceType)
                .andIn(SkuBatch::getFskuId, skuIdList)
                .andEqualTo(SkuBatch::getFbatchStatus, 2);
        Result<List<SkuBatch>> skuBatchs = skuBatchApi.queryByCriteria(batchCriteria);
        if (!skuBatchs.isSuccess()) {
            logger.error("???????????????????????????????????? ????????????{}", searchItemDto.getPageIndex());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        return Result.success(skuBatchs.getData());
    }
    
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ????????????????????????
     * @Param: [supplierAccountQueryDto]
     * @return: PageVo<SupplierAccountVo>
     * @date 2019/9/20 13:49
     */
    @Override
    public SearchItemListVo<SearchItemVo> queryGoodsByCategoryId1(SearchItemDto searchItemDto) {
        //????????????????????????id
        if (searchItemDto.getFcategoryIdL1() == null) {
            throw new BizException(MallExceptionCode.NO_USER_CATEGORY_ID);
        }
        //???????????????????????????????????????
        User user = new User();
        if (null != searchItemDto.getFuid()) {
            if (!searchItemDto.getFverifyStatus().equals(3)) {
                //???????????????????????????????????????
                Result<User> userResult = this.getUser(searchItemDto.getFuid());
                user = userResult.getData();
            }
        }
        CategoryDto categoryDto = new CategoryDto();
        if (null != searchItemDto.getFuid()) {
            categoryDto.setFuid(Long.valueOf(searchItemDto.getFuid()));
        }
        categoryDto.setCurrentPage(searchItemDto.getPageIndex());
        categoryDto.setFcategoryId1(searchItemDto.getFcategoryIdL1().get(0));
        // ?????????PageVo
        SearchItemListVo<SearchItemVo> pageVo = new SearchItemListVo<>();
        pageVo.setIsLogin(searchItemDto.getIsLogin());
        pageVo.setTotalCount(0);
        pageVo.setPageSize(1);
        //??????????????????????????????????????????????????????????????????????????????sku
        Result<List<GoodsSku>> result = this.getSkuList(categoryDto, searchItemDto);
        if (CollectionUtils.isEmpty(result.getData())) {
            return new SearchItemListVo<>(0, categoryDto.getCurrentPage(), categoryDto.getPageSize(), Lists.newArrayList());
        }
        // ????????????
        Result<Integer> totalResult = this.getSkuCount(categoryDto);
        if (0 == totalResult.getData() || Objects.isNull(totalResult.getData())) {
            return new SearchItemListVo<>(0, categoryDto.getCurrentPage(), categoryDto.getPageSize(), Lists.newArrayList());
        }
        //??????????????????????????????????????????????????????
        //??????skuid?????????
        List<Long> skuIdList = new ArrayList<>(result.getData().stream().map(GoodsSku::getFskuId).collect(Collectors.toList()));
        //?????????????????????????????????
        Result<List<SkuBatch>> skuBatchList = this.getSkuBatchList(skuIdList, searchItemDto);
        //???????????????????????????????????????????????????????????????
        Result<List<SkuBatch>> skuBatchs = this.getSkuBatchListByStatu(skuIdList, searchItemDto);
        //??????????????????sku???????????????????????????
        User finalUser = user;
        List<SearchItemVo> searchItemVoList = result.getData().stream().map(goodsSku -> {
            SearchItemVo searchItemVo = dozerMapper.map(goodsSku, SearchItemVo.class);
            searchItemVo.setFimgUrl(goodsSku.getFskuThumbImage());
            //1-------??????????????????
            List<Long> supplierIdList = skuBatchList.getData().stream().filter(s -> s.getFskuId().equals(goodsSku.getFskuId()))
                    .map(SkuBatch::getFsellNum).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(supplierIdList)) {
                //????????????????????????????????????
                Long fsellNum = 0L;
                for (Long sellNum : supplierIdList) {
                    fsellNum += sellNum;
                }
                searchItemVo.setFsellNum(fsellNum);
            }else {
                searchItemVo.setFsellNum(Long.valueOf(0));
            }
            //2---------???????????????1.????????????????????????????????????2.??????sku??????????????????????????????????????????????????????
            // ?????????????????????t_bbc_sku_batch_user_price?????????????????????????????????t_bbc_goods_sku_batch_price??????
            //???????????????????????????????????????????????????????????????
            if (categoryDto.getFuid() != null) {
                //???????????????
                //????????????????????????????????????
                Integer operateType;
                Integer verifyStatus;
                //?????????????????????3?????????????????????token????????????
                if (!searchItemDto.getFverifyStatus().equals(3)) {
                    operateType = finalUser.getFoperateType();
                    verifyStatus = finalUser.getFverifyStatus();
                } else {
                    operateType = searchItemDto.getFoperateType();
                    verifyStatus = searchItemDto.getFverifyStatus();
                }
                
                //??????sku???????????????????????????????????? 0??? 1???
                //1???????????????????????????????????????????????????????????????????????????????????????????????????
                if (goodsSku.getFisUserTypeDiscount().equals(1) && verifyStatus.equals(3)) {
                    //??????sku??????????????????????????????
                    Result<List<SkuUserDiscountConfig>> skuUserDiscountResult = skuUserDiscountConfigApi.queryByCriteria
                            (Criteria.of(SkuUserDiscountConfig.class)
                                    .andEqualTo(SkuUserDiscountConfig::getFskuId, goodsSku.getFskuId())
                                    .andEqualTo(SkuUserDiscountConfig::getFuserTypeId, operateType)
                                    .andEqualTo(SkuUserDiscountConfig::getFisDelete, 0)
                                    .fields(SkuUserDiscountConfig::getFdiscountId));
                    if (!skuUserDiscountResult.isSuccess()) {
                        logger.error("???????????????????????????????????? ??????skuId{} ????????????{}", goodsSku.getFskuId(), operateType);
                        throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                    }
                    //????????????sku???????????????????????????????????????
                    if (CollectionUtils.isEmpty(skuUserDiscountResult.getData())) {
                        List<SkuBatch> skuBatchIdList = skuBatchs.getData().stream().filter(s -> s.getFskuId().equals(goodsSku.getFskuId()))
                                .collect(Collectors.toList());
                        if (CollectionUtils.isNotEmpty(skuBatchIdList)) {
                            //??????????????????
                            this.getMinPrice(skuBatchIdList, searchItemVo,goodsSku.getFskuTaxRate());
                        }else {
                            searchItemVo.setFbatchSellPrice(BigDecimal.valueOf(0));
                        }
                        //???????????????????????????????????????
                    } else {
                        List<SkuBatch> supplierSkuBatchIdList = skuBatchs.getData().stream().filter(s -> s.getFskuId().equals(goodsSku.getFskuId()))
                                .collect(Collectors.toList());
                        if (CollectionUtils.isNotEmpty(supplierSkuBatchIdList)) {
                            List<PackagePriceVo> salePriceList = new ArrayList<>();
                            for (SkuBatch skuBatch : supplierSkuBatchIdList) {
                                //????????????????????????
                                Integer batchPriceType = skuBatch.getFbatchPriceType();
                                Criteria<SkuBatchUserPrice, Object> skuBatchUserPriceCriteria = Criteria.of(SkuBatchUserPrice.class)
                                        .andEqualTo(SkuBatchUserPrice::getFsupplierSkuBatchId, skuBatch.getFsupplierSkuBatchId())
                                        .andEqualTo(SkuBatchUserPrice::getFuserTypeId, operateType);
                                Result<List<SkuBatchUserPrice>> skuBatchUserPriceList = skuBatchUserPriceApi.queryByCriteria(skuBatchUserPriceCriteria
                                        .fields(SkuBatchUserPrice::getFsupplierSkuBatchId,
                                                SkuBatchUserPrice::getFbatchPackageId,
                                                SkuBatchUserPrice::getFbatchSellPrice));
                                if (!skuBatchUserPriceList.isSuccess()) {
                                    logger.error("?????????????????????????????? ??????supplierSkuBatchId{}", skuBatch.getFsupplierSkuBatchId());
                                    throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                                }
                                if (CollectionUtils.isEmpty(skuBatchUserPriceList.getData())) {
                                    throw new BizException(MallExceptionCode.NO_BATCH_USER_PRICE);
                                }
                                //??????????????????id?????????????????????,??????????????????????????????????????????????????????
                                List<PackagePriceVo> packagePriceVoList =  new ArrayList<>();
                                for (SkuBatchUserPrice skuBatchUserPrice :skuBatchUserPriceList.getData()) {
                                    //??????????????????id
                                    Long batchPackageId = skuBatchUserPrice.getFbatchPackageId();
                                    Result<SkuBatchPackage> skuBatchPackage = skuBatchPackageApi.queryById(batchPackageId);
                                    if (!skuBatchPackage.isSuccess()) {
                                        logger.error("?????????????????????????????? ??????batchPackageId{}", batchPackageId);
                                        throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                                    }
                                    if (StringUtils.isEmpty(skuBatchPackage.getData())) {
                                        throw new BizException(MallExceptionCode.NO_BATCH_PRICE);
                                    }
                                    //?????????????????????
                                    Long batchPackageNum = skuBatchPackage.getData().getFbatchPackageNum();
                                    //??????????????????????????????????????? = ?????? / ??????????????????
                                    BigDecimal batchSellPrice = BigDecimal.valueOf(skuBatchUserPrice.getFbatchSellPrice());
                                    BigDecimal onePackagePrice = batchSellPrice.divide(new BigDecimal(batchPackageNum),6,BigDecimal.ROUND_HALF_UP);
                                    //?????????????????????????????????2.?????????????????????4.????????????????????????????????????????????????
                                    if(batchPriceType.equals(2) || batchPriceType.equals(4)){
                                        //??????=??????*??????
                                        BigDecimal tax = batchSellPrice.multiply(new BigDecimal(goodsSku.getFskuTaxRate()))
                                                .divide(MallConstants.TEN_THOUSAND);
                                        onePackagePrice = (batchSellPrice.add(tax)).divide(new BigDecimal(batchPackageNum),6,BigDecimal.ROUND_HALF_UP);
                                    }
                                    //??????????????????
                                    PackagePriceVo packagePriceVo = new PackagePriceVo();
                                    packagePriceVo.setFbatchSellPrice(onePackagePrice);
                                    packagePriceVoList.add(packagePriceVo);
                                    //skuBatchUserPrice.setFbatchSellPrice(Long.valueOf(String.valueOf(onePackagePrice)));
                                }
                                //?????????????????????????????????????????????
                                PackagePriceVo min = packagePriceVoList.stream().min(Comparator.comparing(PackagePriceVo::getFbatchSellPrice)).get();
                                salePriceList.add(min);
                            }
                            //??????????????????????????????
                            PackagePriceVo fbatchSellPrice = salePriceList.stream().min(Comparator.comparing(PackagePriceVo::getFbatchSellPrice)).get();
                            //?????????????????????
                            //indexSkuGoodsVo.setFsupplierSkuBatchId(fbatchSellPrice.getFsupplierSkuBatchId());
                            //????????????????????????Id
                            //indexSkuGoodsVo.setFbatchPackageId(fbatchSellPrice.getFbatchPackageId());
                            //--------????????????
                            BigDecimal sellPrice = fbatchSellPrice.getFbatchSellPrice()
                                    .divide(PageConfigContants.BIG_DECIMAL_100, 2, BigDecimal.ROUND_HALF_UP);
                            searchItemVo.setFbatchSellPrice(sellPrice);
                        }else {
                            searchItemVo.setFbatchSellPrice(BigDecimal.valueOf(0));
                        }
                    }
                } else {
                    //2.???????????????????????????????????????????????????
                    List<SkuBatch> skuBatchIdList = skuBatchs.getData().stream().filter(s -> s.getFskuId().equals(goodsSku.getFskuId()))
                            .collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(skuBatchIdList)) {
                        //??????????????????
                        this.getMinPrice(skuBatchIdList, searchItemVo,goodsSku.getFskuTaxRate());
                    }else {
                        searchItemVo.setFbatchSellPrice(BigDecimal.valueOf(0));
                    }
                }
            }
            return searchItemVo;
        }).collect(Collectors.toList());
        List<SearchItemVo> list = new ArrayList<>();
        //???????????????
        for (SearchItemVo searchItemVo : searchItemVoList) {
            if (searchItemVo != null) {
                list.add(searchItemVo);
            }
        }
        //??????????????????
        if (list.size() > 1) {
            Collections.sort(list, new Comparator<SearchItemVo>() {
                @Override
                public int compare(SearchItemVo o1, SearchItemVo o2) {
                    return o2.getFsellNum().compareTo(o1.getFsellNum());
                }
            });
        }
        //????????????200?????????
        if (categoryDto.getCurrentPage() > 10) {
            return new SearchItemListVo<>(0, categoryDto.getCurrentPage(), categoryDto.getPageSize(), Lists.newArrayList());
        }
        pageVo.setPageSize(searchItemDto.getPageSize());
        pageVo.setCurrentPage(searchItemDto.getPageIndex());
        pageVo.setTotalCount(totalResult.getData());
        pageVo.setList(list);
        // return pageUtils.convert(totalResult.getData(), list, SearchItemVo.class,categoryDto );
        return pageVo;
    }
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ??????????????????
     * @Param: searchItemVo
     * @return: void
     * @date 2019/9/20 13:49
     */
    private void getMinPrice(List<SkuBatch> skuBatchIdList, SearchItemVo searchItemVo,Long skuTaxRate) {
        List<PackagePriceVo> salePriceList = new ArrayList<>();
        for (SkuBatch skuBatch : skuBatchIdList) {
            //????????????????????????
            Integer batchPriceType = skuBatch.getFbatchPriceType();
            Criteria<GoodsSkuBatchPrice, Object> goodsSkuBatchPriceCriteria = Criteria.of(GoodsSkuBatchPrice.class);
            goodsSkuBatchPriceCriteria.andEqualTo(GoodsSkuBatchPrice::getFsupplierSkuBatchId, skuBatch.getFsupplierSkuBatchId());
            Result<List<GoodsSkuBatchPrice>> goodsSkuBatchPriceList = goodsSkuBatchPriceApi.queryByCriteria(goodsSkuBatchPriceCriteria
                    .fields(GoodsSkuBatchPrice::getFsupplierSkuBatchId, GoodsSkuBatchPrice::getFbatchPackageId, GoodsSkuBatchPrice::getFbatchSellPrice));
            if (!goodsSkuBatchPriceList.isSuccess()) {
                logger.error("???????????????????????? ??????supplierSkuBatchId{}", skuBatch.getFsupplierSkuBatchId());
                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
            }
            if (CollectionUtils.isEmpty(goodsSkuBatchPriceList.getData())) {
                throw new BizException(MallExceptionCode.NO_BATCH_PRICE);
            }
            //??????????????????id?????????????????????,??????????????????????????????????????????????????????
            List<PackagePriceVo> packagePriceVoList =  new ArrayList<>();
            for (GoodsSkuBatchPrice goodsSkuBatchPrice :goodsSkuBatchPriceList.getData()) {
                //??????????????????id
                Long batchPackageId = goodsSkuBatchPrice.getFbatchPackageId();
                Result<SkuBatchPackage> skuBatchPackage = skuBatchPackageApi.queryById(batchPackageId);
                if (!skuBatchPackage.isSuccess()) {
                    logger.error("?????????????????????????????? ??????batchPackageId{}", batchPackageId);
                    throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                }
                if (StringUtils.isEmpty(skuBatchPackage.getData())) {
                    throw new BizException(MallExceptionCode.NO_BATCH_PRICE);
                }
                //?????????????????????
                Long batchPackageNum = skuBatchPackage.getData().getFbatchPackageNum();
                //??????????????????????????????????????? = ?????? / ??????????????????
                BigDecimal batchSellPrice = BigDecimal.valueOf(goodsSkuBatchPrice.getFbatchSellPrice());
                BigDecimal onePackagePrice = batchSellPrice.divide(new BigDecimal(batchPackageNum),6,BigDecimal.ROUND_HALF_UP);
                //?????????????????????????????????2.?????????????????????4.????????????????????????????????????????????????
                if(batchPriceType.equals(2) || batchPriceType.equals(4)){
                    //??????=??????*??????
                    BigDecimal tax = batchSellPrice.multiply(new BigDecimal(skuTaxRate))
                            .divide(MallConstants.TEN_THOUSAND);
                    onePackagePrice = (batchSellPrice.add(tax)).divide(new BigDecimal(batchPackageNum),6,BigDecimal.ROUND_HALF_UP);
                }
                //??????????????????
                PackagePriceVo packagePriceVo = new PackagePriceVo();
                packagePriceVo.setFbatchSellPrice(onePackagePrice);
                packagePriceVoList.add(packagePriceVo);
                //goodsSkuBatchPrice.setFbatchSellPrice(onePackagePrice.longValue());
            }
            //????????????????????????????????????????????????
            PackagePriceVo min = packagePriceVoList.stream().min(Comparator.comparing(PackagePriceVo::getFbatchSellPrice)).get();
            salePriceList.add(min);
        }
        //??????????????????????????????
        PackagePriceVo fbatchSellPrice = salePriceList.stream().min(Comparator.comparing(PackagePriceVo::getFbatchSellPrice)).get();
        //?????????????????????
        //indexSkuGoodsVo.setFsupplierSkuBatchId(fbatchSellPrice.getFsupplierSkuBatchId());
        //????????????????????????Id
        //indexSkuGoodsVo.setFbatchPackageId(fbatchSellPrice.getFbatchPackageId());
        //-----------????????????
        BigDecimal sellPrice = fbatchSellPrice.getFbatchSellPrice()
                .divide(PageConfigContants.BIG_DECIMAL_100, 2, BigDecimal.ROUND_HALF_UP);
        searchItemVo.setFbatchSellPrice(sellPrice);
    }
    
    
    /**
     * @author lll
     * @version V1.0
     * @Description: ??????????????????????????????
     * @Param:
     * @return: Result<List   <   GoodsCategoryVo>>
     * @date 2019/9/20 13:49
     */
    @Override
    public Result<List<GoodsCategoryVo>> queryGoodsCategoryList() {
        Result<List<GoodsCategory>> categoryListResultAll = goodsCategoryApi.queryByCriteria(
                //???????????????????????????????????????
                Criteria.of(GoodsCategory.class)
                        .fields(GoodsCategory::getFcategoryName,
                                GoodsCategory::getFcategoryId,
                                GoodsCategory::getFcategoryDesc,
                                GoodsCategory::getFcategorySort,
                                GoodsCategory::getFmodifyTime)
                        .sort(GoodsCategory::getFcategorySort)
                        .andEqualTo(GoodsCategory::getFparentCategoryId, 0)
                        .andEqualTo(GoodsCategory::getFisDelete, 0)
                        .andEqualTo(GoodsCategory::getFisDisplay, 1));
        if (!categoryListResultAll.isSuccess()) {
            throw new BizException(ResultStatus.INTERNAL_SERVER_ERROR);
        }
        List<GoodsCategoryVo> categoryVoList = new LinkedList<>();
        if (CollectionUtils.isNotEmpty(categoryListResultAll.getData())) {
            //????????????
            categoryVoList = dozerHolder.convert(categoryListResultAll.getData(), GoodsCategoryVo.class);
        }
        categoryVoList = categoryVoList.stream().sorted().collect(Collectors.toList());
        return Result.success(categoryVoList);
    }
    
    /**
     * @author fxj
     * @version V1.0
     * @Description: ????????????????????????
     * @Param: [ftype]
     * @return: Result<List   <   GuidePageVo>>
     * @date 2019/9/20 13:49
     */
    @Override
    public Result<List<GuidePageVo>> selectGuidePageVos(Integer ftype) {
        try {
            Criteria<GuidePage, Object> pageCriteria = Criteria.of(GuidePage.class);
            String redisKey = GuidePageContants.GUIDE_PAGE;
            List<Object> result = xyRedisManager.hValues(redisKey);//????????????????????????,?????????????????????GuidePageVo
            if (result == null) {
                pageCriteria.andEqualTo(GuidePage::getFtype, ftype);
                Result<List<GuidePage>> res = guidePageApi.queryByCriteria(pageCriteria.andEqualTo(GuidePage::getFguideType, 0));
                if (!res.isSuccess()) {
                    throw new BizException(ResultStatus.INTERNAL_SERVER_ERROR);
                } else {
                    List<GuidePage> guidePageList = res.getData();
                    List<GuidePageVo> convetVoList = JacksonUtils.jsonTolist(JacksonUtils.objectTojson(guidePageList), GuidePageVo.class);
                    return Result.success(convetVoList);
                }
            } else {
                List<GuidePage> guidePage = JacksonUtils.jsonTolist(JacksonUtils.objectTojson(result), GuidePage.class);
                List<GuidePageVo> convetVoList = JacksonUtils.jsonTolist(JacksonUtils.objectTojson(guidePage), GuidePageVo.class);
                List<GuidePageVo> tempList = convetVoList.stream().filter(index -> {
                    boolean flag = false;
                    if (index.getFtype() == ftype && index.getFguideType() == 0l) {
                        flag = index.getFtype() == ftype && index.getFguideType() == 0l;
                    }
                    return flag;
                }).collect(Collectors.toList());
                return Result.success(tempList);
            }
        } catch (Exception e) {
            throw new BizException(ResultStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
