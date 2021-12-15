package com.leonside.dataroad.flink.processor.aggeration;

import com.leonside.dataroad.common.utils.Asserts;
import com.leonside.dataroad.common.utils.ParameterUtils;
import com.leonside.dataroad.core.aggregations.AggerationEnum;
import com.leonside.dataroad.core.builder.AggerationBuilder;
import com.leonside.dataroad.core.component.ComponentInitialization;
import com.leonside.dataroad.common.constant.JobConfigKeyConstants;
import com.leonside.dataroad.flink.context.FlinkExecuteContext;
import org.apache.commons.collections.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author leon
 */
public class CountWindowAggerationItemProcessor extends AggerationItemProcessor implements ComponentInitialization<FlinkExecuteContext> {

    private Map<String,Object> parameter;

    @Override
    public void initialize(FlinkExecuteContext executeContext,Map<String, Object> parameter) {
        this.parameter = parameter;

        Integer windowSize = ParameterUtils.getInteger(parameter, JobConfigKeyConstants.KEY_AGG_WINDOWSIZE);
        List<?> keyBys = ParameterUtils.getArrayListNullable(parameter, JobConfigKeyConstants.KEY_AGG_KEYBY);
        List<?> fieldAggs = ParameterUtils.getArrayList(parameter, JobConfigKeyConstants.KEY_AGG_FIELDAGG);

        AggerationBuilder.CountWindow countWindow = CollectionUtils.isNotEmpty(keyBys) ?
                new AggerationBuilder.CountWindow(windowSize,keyBys.toArray(new String[]{})) : new AggerationBuilder.CountWindow(windowSize);

        Map<String, List<AggerationEnum>> aggerations = new HashMap<>();
        fieldAggs.stream().forEach(itemMap->{
            Asserts.notEmpty( ((Map)itemMap), " Aggeration field config can not be null");

            ((Map<String,List>)itemMap).forEach((key,value)->{
                List<AggerationEnum> aggerationEnums = aggerations.computeIfAbsent(key, value1 -> new ArrayList<>());
                aggerationEnums.addAll(AggerationEnum.of(value));
            });

        });

        initialize(countWindow, aggerations);

    }
}
