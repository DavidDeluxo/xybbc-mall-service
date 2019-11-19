package com.xingyun.bbc.mallpc.controller;

import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.mallpc.model.dto.BaseDto;
import com.xingyun.bbc.mallpc.model.dto.shoppingcart.ShoppingCartDto;
import com.xingyun.bbc.mallpc.model.validation.ShoppingCartValidator;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 进货单相关接口
 *
 * @author penglu
 * @version 1.0.0
 * @date 2019-11-19
 * @copyright 本内容仅限于深圳市天行云供应链有限公司内部传阅，禁止外泄以及用于其他的商业目的
 */
@RestController
@RequestMapping(value = "shoppingCart", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class ShoppingCartController {

    /**
     * 加入商品
     *
     * @param shoppingCartDto
     * @return
     */
    @PostMapping("add")
    public Result add(@RequestBody @Validated(ShoppingCartValidator.Add.class) ShoppingCartDto shoppingCartDto) {
        return Result.success();
    }

    /**
     * 角标数量
     *
     * @param shoppingCartDto
     * @return
     */
    @PostMapping("qty")
    public Result qty(@RequestBody @Validated(ShoppingCartValidator.Qty.class) ShoppingCartDto shoppingCartDto) {
        return Result.success();
    }

    /**
     * 编辑规格数量
     *
     * @param baseDto
     * @return
     */
    @PostMapping("editNum")
    public Result editNum(@RequestBody @Validated(ShoppingCartValidator.EditNum.class) BaseDto baseDto) {
        return Result.success();
    }

    /**
     * 删除进货单商品
     *
     * @param shoppingCartDto
     * @return
     */
    @PostMapping("delete")
    public Result delete(@RequestBody @Validated(ShoppingCartValidator.Delete.class) ShoppingCartDto shoppingCartDto) {
        return Result.success();
    }

}