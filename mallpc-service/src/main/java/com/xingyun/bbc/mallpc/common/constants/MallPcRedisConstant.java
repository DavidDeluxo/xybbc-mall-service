package com.xingyun.bbc.mallpc.common.constants;

/**
 * @author penglu
 * @version 1.0.0
 * @date 2019-08-21
 * @copyright 本内容仅限于浙江云贸科技有限公司内部传阅，禁止外泄以及用于其他的商业目的
 */
public interface MallPcRedisConstant {

    /**
     * 默认分布式锁过期时间
     */
    long DEFAULT_LOCK_EXPIRING = 5L;

    /**
     * 1分钟 失效时间
     */
    Long EXPIRE_TIME_MIN = 60L;

    /**
     * 1小时 失效时间
     */
    Long EXPIRE_TIME_HOUR = 60 * EXPIRE_TIME_MIN;

    String KEY_PREFIX = "pc_mall:";

    /**
     * 默认分布式锁value
     */
    String DEFAULT_LOCK_VALUE = "";

    /**
     * 首页用户数
     */
    String USER_COUNT = "user_count";
    /**
     * 首页用户数缓存更新分布式锁前缀
     */
    String USER_COUNT_LOCK = "user_count_lock";

    /**
     * 首页一级分类下热门品牌数据
     */
    String INDEX_BRAND = KEY_PREFIX + "index_brand:";

    /**
     * 首页一级分类下热门品牌数据
     */
    String INDEX_BRAND_UPDATE = KEY_PREFIX + "index_brand_lock:";

    /**
     * 首页配置 Banner key
     */
    String PC_MALL_PAGECONFIG_BANNER = KEY_PREFIX + "banner";
    /**
     * pc首页banner更新时redis分布式锁前缀
     */
    String PC_MALL_PAGECONFIG_BANNER_UPDATE = KEY_PREFIX + "banner_lock";

    /**
     * 首页配置 专题位 key
     */
    String PC_MALL_PAGECONFIG_TOPIC = KEY_PREFIX + "topic";
    /**
     * pc首页专题位更新时redis分布式锁前缀
     */
    String PC_MALL_PAGECONFIG_TOPIC_UPDATE = KEY_PREFIX + "topic_lock";

    /**
     * pc发送验证码前缀
     */
    String VERIFY_CODE_PREFIX = "sms:";

    /**
     * pc发送验证码限制前缀
     */
    String VERIFY_CODE_LIMIT_PREFIX = "sms_limit:";

    String ADD_USER_WITHDRAW_LOCK = KEY_PREFIX + "add_user_withdraw_lock";

    /**
     * 首页一级分类楼层商品数据,一级分类id
     */
    String PC_MALL_CATE_SKU = KEY_PREFIX + "index_cate_sku:";

    String TMP_FILE = KEY_PREFIX + "tmp_file:";

    String SALE_SKU_TMP_FILE = TMP_FILE + "sale_sku:";

    /**
     * 导入进货单 临时redis key 前缀
     */
    String IMPORT_SHOPPING_CART_NO_PREFIX = "import_shopping_cart_no:";
}
