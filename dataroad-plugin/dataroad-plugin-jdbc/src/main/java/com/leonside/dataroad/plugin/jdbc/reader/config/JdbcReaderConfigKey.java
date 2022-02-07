package com.leonside.dataroad.plugin.jdbc.reader.config;

import com.leonside.dataroad.common.config.ConfigKey;

/**
 * Configuration Keys for JdbcDataReader
 */
public enum JdbcReaderConfigKey implements ConfigKey {

    KEY_JDBC_URL("jdbcUrl","jdbcURL",true,"", "JdbcUrl"),
    KEY_USER_NAME("username","用户名",true,"", "用户名"),
    KEY_PASSWORD("password","密码",true,"", "密码"),

    KEY_TABLE("table","表名",false,"", "支持配置表名方式和配置自定义SQL两种方式，当采用表名配置方式此处必填"),
    KEY_COLUMN("column","列名",true,"", "当采用表名配置方式此处需配置,数组方式，例如：[\"id\",\"name\"] 或[ {\"name\": \"id\",\"type\": \"int\"}]，当返回全部列可配置[\"*\"]"),
    KEY_WHERE("where","where条件",false,"", "当采用表名配置方式此处可配置"),
    KEY_ORDER_BY_COLUMN("orderByColumn","排序列名",false,"", "当采用表名配置方式此处可配置"),

    KEY_CUSTOM_SQL("customSql","自定义SQL",false,"", "当采用自定义SQL配置方式，此处必填"),

    KEY_SPLIK_KEY("splitKey","分片键",false,"", "分片键，例如：id"),
    KEY_FETCH_SIZE("fetchSize","分批获取大小",false,"0", "设置每次查询按fetchSize分批获取,默认0"),
    KEY_QUERY_TIME_OUT("queryTimeOut","超时时间",false,"0", "查询超时时间,默认0"),

    KEY_INCRE_COLUMN("increColumn","增量字段",false,"", "增量字段，例如：id"),
    KEY_START_LOCATION("startLocation","增量起始值",false,"", "配置增量起始值"),
    KEY_USE_MAX_FUNC("useMaxFunc","是否保存结束位置值",false,"", "用于标记是否保存endLocation位置的一条或多条数据，true：不保存，false(默认)：保存， 某些情况下可能出现最后几条数据被重复记录的情况，可以将此参数配置为true"),

    KEY_POLLING("polling","是否轮询",false,"", "是否轮询，默认false"),
    KEY_POLLING_INTERVAL("pollingInterval","轮询间隔",false,"", "轮询间隔时间，单位毫秒"),

    KEY_REQUEST_ACCUMULATOR_INTERVAL("requestAccumulatorInterval","累加器间隔时间",false,"2", "Accumulator累加器间隔时间，默认2s"),
    ;

    private String name;
    private String cnName;
    private String desc;
    private boolean required;
    private String defaultValue;

    JdbcReaderConfigKey(String name, String cnName, boolean required, String defaultValue, String desc) {
        this.name = name;
        this.cnName = cnName;
        this.desc = desc;
        this.required = required;
        this.defaultValue = defaultValue;
    }
    @Override
    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCnName() {
        return cnName;
    }

    public void setName(String name) {
        this.name = name;
    }
    @Override
    public String getDesc() {
        return desc;
    }

    @Override
    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
