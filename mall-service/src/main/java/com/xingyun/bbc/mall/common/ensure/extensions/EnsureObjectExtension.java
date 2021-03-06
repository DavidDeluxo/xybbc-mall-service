package com.xingyun.bbc.mall.common.ensure.extensions;


import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.mall.common.exception.MallExceptionCode;

/**
 * @author penglu
 * @version 1.0.0
 * @date 2019-08-18
 * @copyright 本内容仅限于浙江云贸科技有限公司内部传阅，禁止外泄以及用于其他的商业目的
 */
public class EnsureObjectExtension {

    private Object param;

    private boolean flag;

    public EnsureObjectExtension(Object param) {
        this.param = param;
    }

    public EnsureObjectExtension isNotNull(MallExceptionCode errorCode) {
        if (this.param == null) {
            throw new BizException(errorCode);
        }
        return this;
    }

    public EnsureObjectExtension isEqualTo(Object param, MallExceptionCode errorCode) {
        this.flag = (param == this.param) || (param != null && this.param != null && this.param.equals(param));
        if (!this.flag) {
            throw new BizException(errorCode);
        }
        return this;
    }

    public EnsureObjectExtension isNotEqualTo(Object param, MallExceptionCode errorCode) {
        if (param != this.param) {
            if (param != null) {
                this.flag = !param.equals(this.param);
            } else if (this.param != null) {
                this.flag = !this.param.equals(param);
            } else {
                this.flag = false;
            }
        } else {
            this.flag = false;
        }
        if (!this.flag) {
            throw new BizException(errorCode);
        }
        return this;
    }

}
