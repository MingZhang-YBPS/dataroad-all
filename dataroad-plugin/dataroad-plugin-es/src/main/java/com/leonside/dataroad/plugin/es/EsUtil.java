
package com.leonside.dataroad.plugin.es;

import com.leonside.dataroad.common.exception.WriteRecordException;
import com.leonside.dataroad.common.utils.DateUtil;
import com.leonside.dataroad.common.utils.StringUtil;
import com.leonside.dataroad.common.utils.TelnetUtil;
import com.leonside.dataroad.plugin.es.config.EsConstants;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.flink.types.Row;
import org.apache.flink.util.Preconditions;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

import java.math.BigDecimal;
import java.util.*;

/**
 * Utilities for ElasticSearch
 *
 */
public class EsUtil {

    public static RestHighLevelClient getClient(String address, String username, String password, Map<String,Object> config) {
        List<HttpHost> httpHostList = new ArrayList<>();
        String[] addr = address.split(",");
        for(String add : addr) {
            String[] pair = add.split(":");
            TelnetUtil.telnet(pair[0], Integer.parseInt(pair[1]));
            httpHostList.add(new HttpHost(pair[0], Integer.parseInt(pair[1]), "http"));
        }

        RestClientBuilder builder = RestClient.builder(httpHostList.toArray(new HttpHost[0]));

        Integer timeout = MapUtils.getInteger(config, EsConstants.KEY_TIMEOUT);
        if (timeout != null){
            builder.setMaxRetryTimeoutMillis(timeout * 1000);
        }

        String pathPrefix = MapUtils.getString(config, EsConstants.KEY_PATH_PREFIX);
        if (StringUtils.isNotEmpty(pathPrefix)){
            builder.setPathPrefix(pathPrefix);
        }
        if(StringUtils.isNotBlank(username)){
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.disableAuthCaching();
                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            });
        }

        return new RestHighLevelClient(builder);
    }

    public static Row jsonMapToRow(Map<String,Object> map, List<String> fields, List<String> types, List<String> values) {
        Preconditions.checkArgument(types.size() == fields.size());
        Row row = Row.withNames();//new Row(fields.size());

        for (int i = 0; i < fields.size(); ++i) {
            String field = fields.get(i);
            if(StringUtils.isNotBlank(field)) {
                String[] parts = field.split("\\.");
                Object value = readMapValue(map, parts);
                row.setField(fields.get(i), value);
            } else {
                Object value = convertValueToAssignType(types.get(i), values.get(i));
                row.setField(fields.get(i), value);
            }

        }

        return row;
    }

    public static Map<String, Object> rowToJsonMap(Row row, List<String> fields, List<String> types) throws WriteRecordException {
//        Preconditions.checkArgument(row.getArity() == fields.size());
        Map<String,Object> jsonMap = new HashMap<>((fields.size()<<2)/3);
        int i = 0;
        try {
            for(; i < fields.size(); ++i) {
                String field = fields.get(i);
                String[] parts = field.split("\\.");
                Map<String, Object> currMap = jsonMap;
                for(int j = 0; j < parts.length - 1; ++j) {
                    String key = parts[j];
                    if(currMap.get(key) == null) {
                        currMap.put(key, new HashMap<String,Object>(16));
                    }
                    currMap = (Map<String, Object>) currMap.get(key);
                }
                String key = parts[parts.length - 1];
                Object col = row.getField(fields.get(i));
                if(col != null) {
                    col = StringUtil.object2col(col, types.get(i), null);
                }

                currMap.put(key, col);
            }
        } catch(Exception ex) {
            String msg = "EsUtil.rowToJsonMap Writing record error: when converting field[" + i + "] in Row(" + row + ")：" + ex.getMessage();
            throw new WriteRecordException( i,msg, ex);
        }

        return jsonMap;
    }


    private static Object readMapValue(Map<String,Object> jsonMap, String[] fieldParts) {
        Map<String,Object> current = jsonMap;
        int i = 0;
        for(; i < fieldParts.length - 1; ++i) {
            if(current.containsKey(fieldParts[i])) {
                current = (Map<String, Object>) current.get(fieldParts[i]);
            } else {
                return null;
            }
        }
        return  current.get(fieldParts[i]);
    }

    private static Object convertValueToAssignType(String columnType, String constantValue) {
        Object column  = null;
        if(org.apache.commons.lang3.StringUtils.isEmpty(constantValue)) {
            return column;
        }

        switch (columnType.toUpperCase()) {
            case "BOOLEAN":
                column = Boolean.valueOf(constantValue);
                break;
            case "SHORT":
            case "INT":
            case "LONG":
                column = NumberUtils.createBigDecimal(constantValue).toBigInteger();
                break;
            case "FLOAT":
            case "DOUBLE":
                column = new BigDecimal(constantValue);
                break;
            case "STRING":
                column = constantValue;
                break;
            case "DATE":
                column = DateUtil.stringToDate(constantValue,null);
                break;
            case "TIMESTAMP":
                column = DateUtil.timestampToString(new Date(constantValue));
                break;

            default:
                throw new IllegalArgumentException("Unsupported column type: " + columnType);
        }
        return column;
    }

    public static String[] getStringArray(Object value){
        if(value == null){
            return null;
        }

        if(value instanceof String){
            String stringValue = value.toString();
            return stringValue.split(",");
        } else if(value instanceof List){
            List list = (List)value;
            String[] array = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                array[i] = list.get(i).toString();
            }

            return array;
        } else {
            return new String[]{value.toString()};
        }
    }
}
