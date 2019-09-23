package com.xingyun.bbc.mall.service;

import com.xingyun.bbc.mall.model.dto.WithdrawDto;
import com.xingyun.bbc.mall.model.dto.WithdrawRateDto;
import com.xingyun.bbc.mall.model.vo.BanksVo;
import com.xingyun.bbc.mall.model.vo.WalletAmountVo;
import com.xingyun.bbc.mall.model.vo.WithdrawRateVo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.List;

/**
 * @author hekaijin
 * @date 2019/9/16 16:34
 * @Description
 */
@Validated
public interface WalletService {


    /**
     * 查询钱包金额
     * @author hekaijin
     * @param uid
     * @return
     */
    WalletAmountVo queryAmount(Long uid);

    /**
     * 校验用户是否设置支付密码
     * @author hekaijin
     * @param uid
     * @return
     */
    Boolean checkPayPwd(Long uid);

    /**
     * 查询提现费率
     * @author hekaijin
     * @param withdrawRateDto
     * @return
     */
    List<WithdrawRateVo> queryWithdrawRate(WithdrawRateDto withdrawRateDto);

    /**
     * 查询银行卡开户行列表
     * @author hekaijin
     * @return
     */
    List<BanksVo> queryBankList();

    /**
     * 用户提现
     * @author hekaijin
     * @param withdrawDto
     * @return
     */
    Boolean withdraw(@Valid WithdrawDto withdrawDto);
}
