package com.xingyun.bbc.mallpc.service.impl;

import com.alibaba.fastjson.JSON;
import com.xingyun.bbc.core.enums.ResultStatus;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.operate.api.OrderConfigApi;
import com.xingyun.bbc.core.operate.api.ShippingCompanyApi;
import com.xingyun.bbc.core.operate.api.TradeTypeApi;
import com.xingyun.bbc.core.operate.po.OrderConfig;
import com.xingyun.bbc.core.operate.po.ShippingCompany;
import com.xingyun.bbc.core.operate.po.TradeType;
import com.xingyun.bbc.core.order.api.*;
import com.xingyun.bbc.core.order.enums.OrderAftersaleStatus;
import com.xingyun.bbc.core.order.enums.OrderAftersaleType;
import com.xingyun.bbc.core.order.model.dto.AftersaleLisDto;
import com.xingyun.bbc.core.order.po.*;
import com.xingyun.bbc.core.query.Criteria;
import com.xingyun.bbc.core.sku.api.GoodsApi;
import com.xingyun.bbc.core.sku.api.GoodsSkuApi;
import com.xingyun.bbc.core.sku.api.SkuBatchApi;
import com.xingyun.bbc.core.sku.po.Goods;
import com.xingyun.bbc.core.sku.po.GoodsSku;
import com.xingyun.bbc.core.sku.po.SkuBatch;
import com.xingyun.bbc.core.supplier.api.SupplierTransportSkuApi;
import com.xingyun.bbc.core.supplier.po.SupplierTransportSku;
import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.mallpc.common.components.DozerHolder;
import com.xingyun.bbc.mallpc.common.exception.MallPcExceptionCode;
import com.xingyun.bbc.mallpc.common.utils.PageHelper;
import com.xingyun.bbc.mallpc.common.utils.PriceUtil;
import com.xingyun.bbc.mallpc.common.utils.ResultUtils;
import com.xingyun.bbc.mallpc.model.dto.aftersale.AftersaleBackDto;
import com.xingyun.bbc.mallpc.model.dto.aftersale.AftersalePcLisDto;
import com.xingyun.bbc.mallpc.model.dto.aftersale.AftersaleSkuInforDto;
import com.xingyun.bbc.mallpc.model.dto.aftersale.ShippingCompanyDto;
import com.xingyun.bbc.mallpc.model.vo.PageVo;
import com.xingyun.bbc.mallpc.model.vo.aftersale.AftersaleBackVo;
import com.xingyun.bbc.mallpc.model.vo.aftersale.AftersaleDetailVo;
import com.xingyun.bbc.mallpc.model.vo.aftersale.AftersaleListVo;
import com.xingyun.bbc.mallpc.service.AftersaleService;
import com.xingyun.bbc.mallpc.service.GoodDetailService;
import com.xingyun.bbc.message.business.MessagePushChannel;
import com.xingyun.bbc.message.model.dto.MsgPushDto;
import com.xingyun.bbc.message.model.dto.MsgTemplateVariableDto;
import com.xingyun.bbc.message.model.enums.PushTypeEnum;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dozer.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@EnableBinding(MessagePushChannel.class)
public class AftersaleServiceImpl implements AftersaleService {

    public static final Logger logger = LoggerFactory.getLogger(AftersaleService.class);

    @Autowired
    private OrderAftersaleApi orderAftersaleApi;

    @Autowired
    private OrderAftersaleAdjustApi orderAftersaleAdjustApi;

    @Autowired
    private OrderAftersaleBackApi orderAftersaleBackApi;

    @Autowired
    private OrderAftersalePicApi orderAftersalePicApi;

    @Autowired
    private OrderApi orderApi;

    @Autowired
    private OrderPaymentApi orderPaymentApi;

    @Autowired
    private GoodsSkuApi goodsSkuApi;

    @Autowired
    private GoodsApi goodsApi;

    @Autowired
    private TradeTypeApi tradeTypeApi;

    @Autowired
    private SkuBatchApi skuBatchApi;

    @Autowired
    private OrderConfigApi orderConfigApi;

    @Autowired
    private ShippingCompanyApi shippingCompanyApi;

    @Autowired
    private SupplierTransportSkuApi supplierTransportSkuApi;

    @Autowired
    private PageHelper pageUtils;

    @Autowired
    private Mapper mapper;

    @Autowired
    private DozerHolder dozerHolder;

    @Resource
    private MessagePushChannel messagePushChannel;

    @Autowired
    private AsyncTaskExecutor asyncTaskExecutor;

    @Autowired
    private GoodDetailService goodDetailService;

    @Override
    public Result<PageVo<AftersaleListVo>> getAftersaleLis(AftersalePcLisDto aftersaleLisDto) {
        AftersaleLisDto dto = mapper.map(aftersaleLisDto, AftersaleLisDto.class);
        //????????????????????????
        Result<Long> countResult = orderAftersaleApi.selectAftersaleCountMallPc(dto);
        if (!countResult.isSuccess()) {
            logger.info("??????user_id {}??????????????????????????????", aftersaleLisDto.getFuid());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (countResult.getData().intValue() == 0) {
            return Result.success();
        }

        Result<List<OrderAftersale>> listResult = orderAftersaleApi.selectAftersaleLisMallPc(dto);

        if (!listResult.isSuccess()) {
            logger.info("??????user_id {}??????????????????????????????", aftersaleLisDto.getFuid());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        PageVo<AftersaleListVo> result = pageUtils.convert(countResult.getData().intValue(), listResult.getData(), AftersaleListVo.class, aftersaleLisDto);

        List<AftersaleListVo> aftersaleList = result.getList();
        List<String> skuCodeList = aftersaleList.stream().map(AftersaleListVo::getFskuCode).distinct().collect(Collectors.toList());

        Result<List<GoodsSku>> goodsSkuResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
                .andIn(GoodsSku::getFskuCode, skuCodeList)
                .fields(GoodsSku::getFgoodsId, GoodsSku::getFskuId, GoodsSku::getFskuCode, GoodsSku::getFskuName, GoodsSku::getFskuThumbImage));
        if (!goodsSkuResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        List<GoodsSku> goodsSkuList = ResultUtils.getListNotEmpty(goodsSkuResult, MallPcExceptionCode.SKU_IS_NONE);
        Map<String, List<GoodsSku>> goodsSkuMap = goodsSkuList.stream().collect(Collectors.groupingBy(GoodsSku::getFskuCode));
        for (AftersaleListVo aftersaleListVo : result.getList()) {
            GoodsSku skuInfor = goodsSkuMap.get(aftersaleListVo.getFskuCode()).get(0);
            if (Objects.nonNull(skuInfor)) {
                aftersaleListVo.setFgoodsId(skuInfor.getFgoodsId());
                aftersaleListVo.setFskuId(skuInfor.getFskuId());
                aftersaleListVo.setFskuName(skuInfor.getFskuName());
                aftersaleListVo.setFskuPic(skuInfor.getFskuThumbImage());
            }
            aftersaleListVo.setFtradeType(this.getTradeType(aftersaleListVo.getFskuCode()));
            aftersaleListVo.setFbatchPackageName(aftersaleListVo.getFbatchPackageNum() + "??????");
            aftersaleListVo.setFunitPrice(PriceUtil.toYuan(aftersaleListVo.getFunitPrice()));
            aftersaleListVo.setFaftersaleNumShow(this.getAftersaleNumShow(aftersaleListVo.getFaftersaleNum(), aftersaleListVo.getFtransportOrderId(), aftersaleListVo.getFskuCode()));
            aftersaleListVo.setFvalidityPeriod(this.getValidityPeriod(aftersaleListVo.getFbatchId()));
            OrderAftersaleBack nameMobile = this.getNameMobile(aftersaleListVo.getForderId());
            aftersaleListVo.setFdeliveryName(nameMobile.getFdeliveryName());
            aftersaleListVo.setFdeliveryMobile(nameMobile.getFdeliveryMobile());
            aftersaleListVo.setFaftersaleTotalAmount(PriceUtil.toYuan(this.getAftersaleTotalAmount(aftersaleListVo.getForderAftersaleId())));
        }
        return Result.success(result);
    }

    @Override
    public Result<AftersaleDetailVo> getAftersaleDetail(String faftersaleId) {
        //??????????????????????????????
        Result<OrderAftersale> aftersaleBasicResult = orderAftersaleApi.queryOneByCriteria(Criteria.of(OrderAftersale.class)
                .andEqualTo(OrderAftersale::getForderAftersaleId, faftersaleId)
                .fields(OrderAftersale::getForderAftersaleId, OrderAftersale::getForderId, OrderAftersale::getFskuCode, OrderAftersale::getFaftersaleNum,
                        OrderAftersale::getFaftersaleStatus, OrderAftersale::getFbatchPackageNum, OrderAftersale::getFunitPrice,
                        OrderAftersale::getFaftersaleReason, OrderAftersale::getFaftersaleType, OrderAftersale::getFtransportOrderId,
                        OrderAftersale::getFbatchId, OrderAftersale::getFcreateTime, OrderAftersale::getFmodifyTime));
        if (!aftersaleBasicResult.isSuccess()) {
            logger.info("??????faftersaleId {}??????????????????????????????", faftersaleId);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }

        //??????sku??????
        AftersaleDetailVo aftersaleDetailVo = mapper.map(aftersaleBasicResult.getData(), AftersaleDetailVo.class);
        AftersaleSkuInforDto skuInfor = this.getSkuInfor(aftersaleDetailVo.getFskuCode());
        aftersaleDetailVo.setFgoodsId(skuInfor.getFgoodsId());
        aftersaleDetailVo.setFskuId(skuInfor.getFskuId());
        aftersaleDetailVo.setFskuName(skuInfor.getFskuName());
        aftersaleDetailVo.setFskuPic(skuInfor.getFskuThumbImage());
        aftersaleDetailVo.setFtradeType(skuInfor.getFtradeType());

        aftersaleDetailVo.setFbatchPackageName(aftersaleDetailVo.getFbatchPackageNum() + "??????");
        aftersaleDetailVo.setFrealPayAmount(PriceUtil.toYuan(aftersaleDetailVo.getFunitPrice().multiply(new BigDecimal(aftersaleDetailVo.getFaftersaleNum()))));//???????????? = ?????? * ??????
        aftersaleDetailVo.setFunitPrice(PriceUtil.toYuan(aftersaleDetailVo.getFunitPrice()));
        aftersaleDetailVo.setFaftersaleNumShow(this.getAftersaleNumShow(aftersaleDetailVo.getFaftersaleNum(), aftersaleDetailVo.getFtransportOrderId(), aftersaleDetailVo.getFskuCode()));

        //????????????
        aftersaleDetailVo.setFvalidityPeriod(this.getValidityPeriod(aftersaleDetailVo.getFbatchId()));

        //?????????????????????--????????????
        Result<List<OrderAftersalePic>> afterPicResult = orderAftersalePicApi.queryByCriteria(Criteria.of(OrderAftersalePic.class)
                .andEqualTo(OrderAftersalePic::getForderAftersaleId, faftersaleId)
                .andEqualTo(OrderAftersalePic::getFpicType, 1)
                .fields(OrderAftersalePic::getFaftersalePic));
        if (!afterPicResult.isSuccess()) {
            logger.info("??????faftersaleId {}??????????????????????????????", faftersaleId);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (!CollectionUtils.isEmpty(afterPicResult.getData())) {
            aftersaleDetailVo.setFadminAfterSalePic(afterPicResult.getData().stream().map(OrderAftersalePic::getFaftersalePic).collect(Collectors.toList()));
        }

        //?????????????????????
        Long faftersaleTotalAmount = this.getAftersaleTotalAmount(faftersaleId);
        aftersaleDetailVo.setFaftersaleTotalAmount(PriceUtil.toYuan(faftersaleTotalAmount));

        //????????????faftersale_status 1????????????2????????????3????????????4????????????6????????????7????????????8????????? 9????????????10???????????????11???????????????12??????????????????13???????????????14?????????????????????15????????????
        //????????????faftersale_type 1 ?????? 2 ???????????? ??????????????????????????????
        if (OrderAftersaleType.RETURN_MONEY_AND_GOODS.getCode().equals(aftersaleDetailVo.getFaftersaleType())) {

            //??????????????? 6 ???????????????????????????????????????????????????
            if (OrderAftersaleStatus.WAIT_RETURN_GOODS.getCode().equals(aftersaleDetailVo.getFaftersaleStatus())) {
                Result<OrderAftersaleBack> aftersaleBackResult = orderAftersaleBackApi.queryOneByCriteria(Criteria.of(OrderAftersaleBack.class)
                        .andEqualTo(OrderAftersaleBack::getForderAftersaleId, faftersaleId)
                        .fields(OrderAftersaleBack::getFdeliveryName, OrderAftersaleBack::getFdeliveryMobile, OrderAftersaleBack::getFdeliveryProvince,
                                OrderAftersaleBack::getFdeliveryCity, OrderAftersaleBack::getFdeliveryArea, OrderAftersaleBack::getFdeliveryAddr, OrderAftersaleBack::getFbackRemark,
                                OrderAftersaleBack::getFbackStatus, OrderAftersaleBack::getFbackLogisticsOrder, OrderAftersaleBack::getFlogisticsCompanyId));
                if (!aftersaleBackResult.isSuccess()) {
                    logger.info("??????faftersaleId {}??????????????????????????????", faftersaleId);
                    throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                }
                OrderAftersaleBack aftersaleBack = aftersaleBackResult.getData();
                if (null != aftersaleBack) {
                    aftersaleDetailVo.setFdeliveryName(aftersaleBack.getFdeliveryName());
                    aftersaleDetailVo.setFdeliveryMobile(aftersaleBack.getFdeliveryMobile());
                    aftersaleDetailVo.setFdeliveryProvince(aftersaleBack.getFdeliveryProvince());
                    aftersaleDetailVo.setFdeliveryCity(aftersaleBack.getFdeliveryCity());
                    aftersaleDetailVo.setFdeliveryArea(aftersaleBack.getFdeliveryArea());
                    aftersaleDetailVo.setFdeliveryAddr(aftersaleBack.getFdeliveryAddr());
                    aftersaleDetailVo.setFbackStatus(aftersaleBack.getFbackStatus());
                    aftersaleDetailVo.setFbackRemark(aftersaleBack.getFbackRemark());
                    aftersaleDetailVo.setFbackLogisticsOrder(aftersaleBack.getFbackLogisticsOrder());
                    if (aftersaleBack.getFlogisticsCompanyId().intValue() != 0) {
                        Result<ShippingCompany> shippingCompanyResult = shippingCompanyApi.queryOneByCriteria(Criteria.of(ShippingCompany.class)
                                .andEqualTo(ShippingCompany::getFshippingCompanyId, aftersaleBack.getFlogisticsCompanyId())
                                .fields(ShippingCompany::getFshippingName));
                        if (shippingCompanyResult.isSuccess() && null != shippingCompanyResult.getData()) {
                            aftersaleDetailVo.setFlogisticsCompany(shippingCompanyResult.getData().getFshippingName());
                        }
                    }
                }

                //???????????????????????????
                Result<OrderConfig> orderConfigResult = orderConfigApi.queryOneByCriteria(Criteria.of(OrderConfig.class)
                        .andEqualTo(OrderConfig::getForderConfigType, 3)
                        .fields(OrderConfig::getFminute));
                if (!orderConfigResult.isSuccess()) {
                    logger.info("???????????????????????????????????????????????????");
                    throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                }
                Long fminute = orderConfigResult.getData().getFminute();
                Long time = aftersaleBasicResult.getData().getFmodifyTime().getTime();
                Date reGoodsTime = new Date(fminute * 60 * 1000 + time);
                aftersaleDetailVo.setFreGoodsTime(reGoodsTime);
            }
        }
        if (aftersaleDetailVo.getFaftersaleStatus() <= OrderAftersaleStatus.WAIT_FINANCE_VERIFY.getCode()) {
            aftersaleDetailVo.setFrefundTime(aftersaleBasicResult.getData().getFcreateTime());
        } else {
            aftersaleDetailVo.setFrefundTime(aftersaleBasicResult.getData().getFmodifyTime());
        }
        return Result.success(aftersaleDetailVo);
    }

    private String getAftersaleNumShow(Integer faftersaleNum, String ftransportOrderId, String fskuCode) {
        //????????????????????? faftersaleNum ?????????(????????????) ??????faftersaleNum/????????????
        if (StringUtils.isEmpty(ftransportOrderId)) {
            return faftersaleNum.toString();
        } else {
            Result<SupplierTransportSku> supplierTransportSkuResult = supplierTransportSkuApi.queryOneByCriteria(Criteria.of(SupplierTransportSku.class)
                    .andEqualTo(SupplierTransportSku::getFskuCode, fskuCode)
                    .andEqualTo(SupplierTransportSku::getFtransportOrderId, ftransportOrderId)
                    .fields(SupplierTransportSku::getFbatchPackageNum, SupplierTransportSku::getFskuNum));
            if (!supplierTransportSkuResult.isSuccess()) {
                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
            }
            if (null != supplierTransportSkuResult.getData()) {
                SupplierTransportSku data = supplierTransportSkuResult.getData();
                Long fbatchPackageNum = data.getFbatchPackageNum();
                Integer fskuNum = data.getFskuNum();
                Long total = fbatchPackageNum * fskuNum;
                return new StringBuffer(faftersaleNum.toString()).append("/").append(total).toString();
            }
            return "";
        }
    }

    @Override
    public Result<List<ShippingCompanyDto>> getShippingCompanyLis(ShippingCompanyDto shippingCompanyDto) {
        Criteria<ShippingCompany, Object> criteria = Criteria.of(ShippingCompany.class);
        if (!StringUtils.isEmpty(shippingCompanyDto.getFshippingName())) {
            criteria.andLike(ShippingCompany::getFshippingName, shippingCompanyDto.getFshippingName() + "%");
        }
        Result<List<ShippingCompany>> listResult = shippingCompanyApi.queryByCriteria(criteria);
        List<ShippingCompanyDto> result = dozerHolder.convert(listResult.getData(), ShippingCompanyDto.class);
        return Result.success(result);
    }

    @Override
    @GlobalTransactional
    public Result modifyAftersaleBack(AftersaleBackDto aftersaleBackDto) {
        //??????????????????
        Result<OrderAftersale> statusResult = orderAftersaleApi.queryOneByCriteria(Criteria.of(OrderAftersale.class)
                .andEqualTo(OrderAftersale::getForderAftersaleId, aftersaleBackDto.getForderAftersaleId())
                .fields(OrderAftersale::getFaftersaleStatus));
        if (!statusResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (!OrderAftersaleStatus.WAIT_RETURN_GOODS.getCode().equals(statusResult.getData().getFaftersaleStatus())) {
            return Result.success();
        }
        //??????orderAftersaleBack id
        Result<OrderAftersaleBack> aftersaleBackResult = orderAftersaleBackApi.queryOneByCriteria(Criteria.of(OrderAftersaleBack.class)
                .andEqualTo(OrderAftersaleBack::getForderAftersaleId, aftersaleBackDto.getForderAftersaleId())
                .fields(OrderAftersaleBack::getFaftersaleBackId));
        if (!aftersaleBackResult.isSuccess() || null == aftersaleBackResult.getData()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        OrderAftersaleBack aftersaleBack = mapper.map(aftersaleBackDto, OrderAftersaleBack.class);
        aftersaleBack.setFaftersaleBackId(aftersaleBackResult.getData().getFaftersaleBackId());
        //?????????????????????
        Result<Integer> insResult = orderAftersaleBackApi.updateNotNull(aftersaleBack);
        if (!insResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null != aftersaleBackDto.getFpicStr()) {
            String[] picStr = aftersaleBackDto.getFpicStr().split(",");
            List<OrderAftersalePic> aftersalePicLis = new ArrayList<>();
            Date nowDate = new Date();
            for (String pic : picStr) {
                OrderAftersalePic aftersalePic = new OrderAftersalePic();
                aftersalePic.setForderAftersaleId(aftersaleBackDto.getForderAftersaleId());
                aftersalePic.setFpicType(2);
                aftersalePic.setFaftersalePic(pic);
                aftersalePic.setFcreateTime(nowDate);
                aftersalePic.setFmodifyTime(nowDate);
                aftersalePicLis.add(aftersalePic);
            }
            Result<Integer> picInsResult = orderAftersalePicApi.createBatch(aftersalePicLis);
            if (!picInsResult.isSuccess()) {
                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
            }
        }
        //??????????????????--???????????????????????????--??????????????????
        Result<OrderAftersale> queryAfterSaleResult = orderAftersaleApi.queryOneByCriteria(Criteria.of(OrderAftersale.class)
                .andEqualTo(OrderAftersale::getForderAftersaleId, aftersaleBackDto.getForderAftersaleId())
                .fields(OrderAftersale::getFmodifyTime, OrderAftersale::getFsupplierId, OrderAftersale::getFsupplierOrderId));
        if (!queryAfterSaleResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        OrderAftersale upAftersale = queryAfterSaleResult.getData();
        upAftersale.setForderAftersaleId(aftersaleBackDto.getForderAftersaleId());
        upAftersale.setFaftersaleStatus(OrderAftersaleStatus.WAIT_RETURN_MONEY.getCode());
        Result<Integer> aftersaleResult = orderAftersaleApi.updateNotNull(upAftersale);
        if (!aftersaleResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        //??????????????????????????? ???????????????

        asyncTaskExecutor.execute(() -> sendMessage(upAftersale));

        return Result.success();
    }

    @Override
    public Result<AftersaleBackVo> getAftersaleBackShipping(String faftersaleId) {
        Result<OrderAftersaleBack> aftersaleBackResult = orderAftersaleBackApi.queryOneByCriteria(Criteria.of(OrderAftersaleBack.class)
                .andEqualTo(OrderAftersaleBack::getForderAftersaleId, faftersaleId)
                .fields(OrderAftersaleBack::getForderAftersaleId, OrderAftersaleBack::getFlogisticsCompanyId, OrderAftersaleBack::getFbackLogisticsOrder,
                        OrderAftersaleBack::getFbackRemark, OrderAftersaleBack::getFbackMobile, OrderAftersaleBack::getFdeliveryName,
                        OrderAftersaleBack::getFdeliveryMobile, OrderAftersaleBack::getFdeliveryProvince, OrderAftersaleBack::getFdeliveryCity,
                        OrderAftersaleBack::getFdeliveryArea, OrderAftersaleBack::getFdeliveryAddr, OrderAftersaleBack::getFbackStatus));
        if (!aftersaleBackResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        AftersaleBackVo aftersaleBackVo = new AftersaleBackVo();
        if (null == aftersaleBackResult.getData()) {
            return Result.success(aftersaleBackVo);
        }
        aftersaleBackVo = mapper.map(aftersaleBackResult.getData(), AftersaleBackVo.class);
        //????????????????????????
        Result<ShippingCompany> shippingCompanyResult = shippingCompanyApi.queryOneByCriteria(Criteria.of(ShippingCompany.class)
                .andEqualTo(ShippingCompany::getFshippingCompanyId, aftersaleBackVo.getFlogisticsCompanyId())
                .fields(ShippingCompany::getFshippingName));
        if (!shippingCompanyResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        aftersaleBackVo.setFlogisticsCompanyName(null != shippingCompanyResult.getData() ? shippingCompanyResult.getData().getFshippingName() : "");
        Result<List<OrderAftersalePic>> picResult = orderAftersalePicApi.queryByCriteria(Criteria.of(OrderAftersalePic.class)
                .andEqualTo(OrderAftersalePic::getFpicType, 2)
                .andEqualTo(OrderAftersalePic::getForderAftersaleId, faftersaleId)
                .fields(OrderAftersalePic::getFaftersalePic));
        if (!picResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (!CollectionUtils.isEmpty(picResult.getData())) {
            aftersaleBackVo.setFuserAftersalePic(picResult.getData().stream().map(OrderAftersalePic::getFaftersalePic).collect(Collectors.toList()));
        }
        return Result.success(aftersaleBackVo);
    }

    //sku????????????
    private AftersaleSkuInforDto getSkuInfor(String skuCode) {
        Result<GoodsSku> goodsSkuResult = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFskuCode, skuCode)
                .fields(GoodsSku::getFgoodsId, GoodsSku::getFskuId, GoodsSku::getFskuName, GoodsSku::getFskuThumbImage));
        if (!goodsSkuResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        AftersaleSkuInforDto result = new AftersaleSkuInforDto();
        GoodsSku goodsSkuData = goodsSkuResult.getData();
        result.setFgoodsId(null != goodsSkuData ? goodsSkuData.getFgoodsId() : 0l);
        result.setFskuId(null != goodsSkuData ? goodsSkuData.getFskuId() : 0l);
        result.setFskuName(null != goodsSkuData ? goodsSkuData.getFskuName() : "");
        result.setFskuThumbImage(null != goodsSkuData ? goodsSkuData.getFskuThumbImage() : "");
        result.setFtradeType("");
        if (null != goodsSkuData) {
            Long fgoodsId = goodsSkuResult.getData().getFgoodsId();
            Result<Goods> goodsResult = goodsApi.queryOneByCriteria(Criteria.of(Goods.class)
                    .andEqualTo(Goods::getFgoodsId, fgoodsId)
                    .fields(Goods::getFtradeId));
            if (goodsResult.isSuccess() && null != goodsResult.getData()) {
                Long ftradeId = goodsResult.getData().getFtradeId();
                Result<TradeType> tradeTypeResult = tradeTypeApi.queryOneByCriteria(Criteria.of(TradeType.class)
                        .andEqualTo(TradeType::getFtradeTypeId, ftradeId).fields(TradeType::getFtradeType));
                if (tradeTypeResult.isSuccess() && null != tradeTypeResult.getData()) {
                    result.setFtradeType(tradeTypeResult.getData().getFtradeType());
                }
            }
        }
        return result;
    }

    //??????????????????
    private String getTradeType(String fskuCode) {
        String tradeType = "";
        Result<GoodsSku> goodsSkuResult = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFskuCode, fskuCode)
                .fields(GoodsSku::getFgoodsId));
        if (goodsSkuResult.isSuccess() && null != goodsSkuResult.getData()) {
            Long fgoodsId = goodsSkuResult.getData().getFgoodsId();
            Result<Goods> goodsResult = goodsApi.queryOneByCriteria(Criteria.of(Goods.class)
                    .andEqualTo(Goods::getFgoodsId, fgoodsId)
                    .fields(Goods::getFtradeId));
            if (goodsResult.isSuccess() && null != goodsResult.getData()) {
                Long ftradeId = goodsResult.getData().getFtradeId();
                Result<TradeType> tradeTypeResult = tradeTypeApi.queryOneByCriteria(Criteria.of(TradeType.class).andEqualTo(TradeType::getFtradeTypeId, ftradeId).fields(TradeType::getFtradeType));
                if (tradeTypeResult.isSuccess() && null != tradeTypeResult.getData()) {
                    tradeType = tradeTypeResult.getData().getFtradeType();
                }
            }
        }
        return tradeType;
    }

    //??????
    private String getValidityPeriod(String fbatchId) {
        String validityPeriod = "";
        DateFormat sdf = new SimpleDateFormat("yyyy-MM");
        Result<SkuBatch> skuBatchResult = skuBatchApi.queryOneByCriteria(Criteria.of(SkuBatch.class)
                .andEqualTo(SkuBatch::getFsupplierSkuBatchId, fbatchId)
                .fields(SkuBatch::getFqualityStartDate, SkuBatch::getFqualityEndDate));
        SkuBatch data = skuBatchResult.getData();
        if (skuBatchResult.isSuccess() && null != data) {
            StringBuffer sb = new StringBuffer();
            if (Objects.nonNull(data.getFqualityStartDate()) && Objects.nonNull(data.getFqualityEndDate())) {
                Calendar startDateCal = Calendar.getInstance();
                Calendar endDateCal = Calendar.getInstance();
                startDateCal.setTime(data.getFqualityStartDate());
                endDateCal.setTime(data.getFqualityEndDate());
                if (goodDetailService.isSameYearAndMonth(startDateCal, endDateCal)) {
                    validityPeriod = sb.append(sdf.format(data.getFqualityStartDate())).toString();
                } else {
                    validityPeriod = sb.append(sdf.format(data.getFqualityStartDate())).append("~").append(sdf.format(data.getFqualityEndDate())).toString();
                }
            }
        }
        return validityPeriod;
    }


    //???????????????????????????
    private OrderAftersaleBack getNameMobile(String forderId) {
        String name = "";
        String mobile = "";
        Result<Order> orderResult = orderApi.queryOneByCriteria(Criteria.of(Order.class)
                .andEqualTo(Order::getForderId, forderId).fields(Order::getForderPaymentId));
        Order orderData = orderResult.getData();
        if (orderResult.isSuccess() && null != orderData) {
            String forderPaymentId = orderData.getForderPaymentId();
            Result<OrderPayment> orderPaymentResult = orderPaymentApi.queryOneByCriteria(Criteria.of(OrderPayment.class)
                    .andEqualTo(OrderPayment::getForderPaymentId, forderPaymentId)
                    .fields(OrderPayment::getFdeliveryName, OrderPayment::getFdeliveryMobile));
            OrderPayment orderPaymentData = orderPaymentResult.getData();
            if (orderPaymentResult.isSuccess() && null != orderPaymentData) {
                name = orderPaymentData.getFdeliveryName();
                mobile = orderPaymentData.getFdeliveryMobile();
            }
        }
        OrderAftersaleBack result = new OrderAftersaleBack();
        result.setFdeliveryName(name);
        result.setFdeliveryMobile(mobile);
        return result;
    }

    //?????????????????????
    private Long getAftersaleTotalAmount(String faftersaleId) {
        //?????????????????????
        Result<OrderAftersaleAdjust> aftersaleAdjustResult = orderAftersaleAdjustApi.queryOneByCriteria(Criteria.of(OrderAftersaleAdjust.class)
                .andEqualTo(OrderAftersaleAdjust::getForderAftersaleId, faftersaleId)
                .fields(OrderAftersaleAdjust::getFaftersaleTotalAmount));
        if (!aftersaleAdjustResult.isSuccess()) {
            logger.info("??????faftersaleId {}?????????????????????????????????", faftersaleId);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        return aftersaleAdjustResult.getData().getFaftersaleTotalAmount();
    }

    private void sendMessage(OrderAftersale orderAftersale) {
        MsgPushDto msgPushDto = new MsgPushDto();
        MsgTemplateVariableDto msgTemplateVariableDto = new MsgTemplateVariableDto();
        if (orderAftersale.getFaftersaleStatus().toString().equals(OrderAftersaleStatus.WAIT_RETURN_MONEY.getCode().toString())) {
            msgTemplateVariableDto.setForderAftersaleId(orderAftersale.getForderAftersaleId());
            msgTemplateVariableDto.setFsupplierOrderId(orderAftersale.getFsupplierOrderId());
            msgPushDto.setMsgTemplateVariable(msgTemplateVariableDto);
            msgPushDto.setSystemTemplateType(8);
            msgPushDto.setPushType(PushTypeEnum.SYSTEM_NOTIFY.getKey());
            msgPushDto.setSubjectType(2);
            msgPushDto.setSubjectId(orderAftersale.getFsupplierId());
            Message<MsgPushDto> message = MessageBuilder.withPayload(msgPushDto).build();
            boolean result = messagePushChannel.systemNoticeOut().send(message);
            if (result) {
                log.info("???????????????????????????????????????message={}", JSON.toJSONString(msgPushDto));
            } else {
                log.warn("???????????????????????????????????????message={}", JSON.toJSONString(msgPushDto));
            }
        }
    }
}
