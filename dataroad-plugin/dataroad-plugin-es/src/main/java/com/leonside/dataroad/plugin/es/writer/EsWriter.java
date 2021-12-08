
package com.leonside.dataroad.plugin.es.writer;

import com.leonside.dataroad.common.spi.ItemWriter;
import com.leonside.dataroad.common.utils.MapParameterUtils;
import com.leonside.dataroad.flink.context.FlinkExecuteContext;
import com.leonside.dataroad.flink.writer.BaseItemWriter;
import com.leonside.dataroad.plugin.es.EsConfigKeys;
import org.apache.commons.collections.CollectionUtils;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.types.Row;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The writer plugin of ElasticSearch
 *
 */
public class EsWriter extends BaseItemWriter implements ItemWriter<FlinkExecuteContext, DataStream<Row>> {

    public static final int DEFAULT_BULK_ACTION = 100;

    public static final String DEFAULT_DOC = "_doc";

    private String address;
    private String username;
    private String password;
    private String index;
    private String type;
    private int bulkAction;
    private Map<String,Object> clientConfig;
    private List<String> columnTypes;
    private List<String> columnNames;
    private List<String> idColumnNames;
    private List<String> idColumnTypes;
    private List<String> idColumnValues;

    @Override
    public void initialize(FlinkExecuteContext executeContext, Map<String, Object> parameter) {
        super.initialize(executeContext, parameter);

        address = MapParameterUtils.getString(parameter, EsConfigKeys.KEY_ADDRESS);
        username = MapParameterUtils.getStringNullable(parameter, EsConfigKeys.KEY_USERNAME);
        password = MapParameterUtils.getStringNullable(parameter, EsConfigKeys.KEY_PASSWORD);
        type = MapParameterUtils.getStringNullable(parameter, EsConfigKeys.KEY_TYPE,DEFAULT_DOC);
        index = MapParameterUtils.getString(parameter, EsConfigKeys.KEY_INDEX);
        bulkAction = MapParameterUtils.getIntegerNullable(parameter, EsConfigKeys.KEY_BULK_ACTION, DEFAULT_BULK_ACTION);

        clientConfig = new HashMap<>();
        clientConfig.put(EsConfigKeys.KEY_TIMEOUT, MapParameterUtils.getIntegerNullable(parameter, EsConfigKeys.KEY_TIMEOUT));
        clientConfig.put(EsConfigKeys.KEY_PATH_PREFIX, MapParameterUtils.getStringNullable(parameter, EsConfigKeys.KEY_PATH_PREFIX));

        List columns = MapParameterUtils.getArrayList(parameter, EsConfigKeys.KEY_COLUMN);
        if(CollectionUtils.isNotEmpty(columns)) {
            columnTypes = new ArrayList<>();
            columnNames = new ArrayList<>();
            for(int i = 0; i < columns.size(); ++i) {
                Map sm = (Map) columns.get(i);
                columnNames.add((String) sm.get(EsConfigKeys.KEY_COLUMN_NAME));
                columnTypes.add((String) sm.get(EsConfigKeys.KEY_COLUMN_TYPE));
            }
        }

        List idColumns = (List) MapParameterUtils.getArrayList(parameter, EsConfigKeys.KEY_ID_COLUMN);
        if( idColumns != null &&  idColumns.size() != 0) {
            idColumnNames = new ArrayList<>();
            idColumnTypes = new ArrayList<>();
            idColumnValues = new ArrayList<>();
            for(int i = 0; i <  idColumns.size(); ++i) {
                Map<String,Object> sm =
                        (Map)  idColumns.get(i);
                Object ind = sm.get(EsConfigKeys.KEY_ID_COLUMN_NAME);
                idColumnNames.add((String)ind);
//                if(ind == null) {
//                    idColumnIndices.add(-1);
//                } else if(ind instanceof Double) {
//                    idColumnIndices.add(((Double) ind).intValue());
//                } else if (ind instanceof Integer) {
//                    idColumnIndices.add(((Integer) ind).intValue());
//                } else if (ind instanceof Long) {
//                    idColumnIndices.add(((Long) ind).intValue());
//                } else if(ind instanceof String) {
//                    idColumnIndices.add(Integer.valueOf((String) ind));
//                }
                idColumnTypes.add((String) sm.get(EsConfigKeys.KEY_ID_COLUMN_TYPE));
                idColumnValues.add((String) sm.get(EsConfigKeys.KEY_ID_COLUMN_VALUE));
            }
        }

    }

    @Override
    public void write(FlinkExecuteContext executeContext, DataStream<Row> items) {
        EsOutputFormatBuilder builder = new EsOutputFormatBuilder();
        builder.setAddress(address)
                .setUsername(username)
                .setPassword(password)
                .setIndex(index)
                .setType(type)
                .setBatchInterval(bulkAction)
                .setClientConfig(clientConfig)
                .setColumnNames(columnNames)
                .setColumnTypes(columnTypes)
                .setIdColumnIndices(idColumnNames)
                .setIdColumnTypes(idColumnTypes)
                .setIdColumnValues(idColumnValues)
                .setMonitorUrls(monitorUrls)
                .setErrors(errors);
//        builder.setDirtyPath(dirtyPath);
//        builder.setDirtyHadoopConfig(dirtyHadoopConfig);
//        builder.setSrcCols(srcCols);

            createOutput(items, builder.finish());
    }
}