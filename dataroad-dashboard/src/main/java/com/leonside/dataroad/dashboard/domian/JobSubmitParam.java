package com.leonside.dataroad.dashboard.domian;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TSFBuilder;
import com.google.common.collect.Lists;
import com.leonside.dataroad.DataroadEngine;
import com.leonside.dataroad.common.config.Options;
import com.leonside.dataroad.common.utils.JsonUtil;
import com.leonside.dataroad.config.domain.JobConfigs;
import com.leonside.dataroad.config.job.JsonJobSchemaParser;
import com.leonside.dataroad.dashboard.configuration.DataroadProperties;
import com.leonside.dataroad.flink.utils.PluginJarHelper;
import lombok.Data;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author leon
 */
@Data
public class JobSubmitParam {

    private String entryClass;
    private String programArgs;
//    private int parallelism = -2;
    private String allowNonRestoredState;
    private String savepointPath;

    public static JobSubmitParamBuilder builder(){
        return new JobSubmitParamBuilder();
    }
    public static class JobSubmitParamBuilder{

        public static final String SAVEPOINTS_DIR_KEY = "state.savepoints.dir";
        public static final String PARALLELISM_KEY = "parallelism.default";

        private String jobId;
        private String jobFlowJson;
//        private String extLibPath[];
        private Integer parallelism ;
        private String savepointPath;
        private String allowNonRestoredState;
        private String confProp;
        private DataroadProperties dataroadProperties;

        public JobSubmitParamBuilder setAllowNonRestoredState(String allowNonRestoredState) {
            this.allowNonRestoredState = allowNonRestoredState;
            return this;
        }

        public JobSubmitParamBuilder setJobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public JobSubmitParamBuilder setJobFlowJson(String jobFlowJson) {
            this.jobFlowJson = jobFlowJson;
            return this;
        }

        public JobSubmitParamBuilder setParallelism(Integer parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        public JobSubmitParamBuilder setSavepointPath(String savepointPath) {
            this.savepointPath = savepointPath;
            return this;
        }

        public JobSubmitParamBuilder setDataroadProperties(DataroadProperties dataroadProperties) {
            this.dataroadProperties = dataroadProperties;
            return this;
        }

        public JobSubmitParamBuilder setConfProp(String confProp) {
            this.confProp = confProp;
            return this;
        }

        public JobSubmitParam build() throws JsonProcessingException, UnsupportedEncodingException {
            JobSubmitParam jobSubmitParam = new JobSubmitParam();
            jobSubmitParam.setEntryClass(DataroadEngine.class.getName());

            //创建 confProp
            Map<String,Object> confPropMap = StringUtils.isNotEmpty(confProp) ? JsonUtil.getInstance().readJson(confProp, HashMap.class) : new HashMap<>();
//            if(StringUtils.isNotEmpty(savepointPath)){
//                confProp.put(SAVEPOINTS_DIR_KEY, savepointPath);
//            }
            if(parallelism != null){
                confPropMap.put(PARALLELISM_KEY, parallelism);
//                jobSubmitParam.setParallelism(parallelism);
            }


            //创建conf
            String confURL = dataroadProperties.getConfURL(jobId);

            //创建extLibPath
            //获取依赖jar
            String dependOnJarsURL = null;
            JobConfigs jobConfigs = new JsonJobSchemaParser().parserJSON(jobFlowJson);
            Set<String> dependOnJars = PluginJarHelper.findDependOnJars(dataroadProperties.getDataroadPluginPath(), jobConfigs.getJob().getAllComponents());
            if(CollectionUtils.isNotEmpty(dependOnJars)){
                dependOnJarsURL = dependOnJars.stream().map(dependOnJar -> {
                    String[] splitPath = StringUtils.split(dependOnJar,"/");
                    String endpath = splitPath[splitPath.length - 2] + "/" + splitPath[splitPath.length - 1];
                    return dataroadProperties.getComponentArchiveURL(endpath);
                }).collect(Collectors.joining(","));
            }

            StringBuilder sb = new StringBuilder();
            sb.append(" -conf " + confURL);
            if(StringUtils.isNotEmpty(dependOnJarsURL)){
                sb.append(" -extLibPath " + dependOnJarsURL);
            }
            if(MapUtils.isNotEmpty(confPropMap)){
                sb.append(" -confProp " + URLEncoder.encode(JsonUtil.getInstance().writeJson(confPropMap),"UTF-8"));
            }

            jobSubmitParam.setProgramArgs(sb.toString());
            jobSubmitParam.setSavepointPath(this.savepointPath);
            jobSubmitParam.setAllowNonRestoredState(this.allowNonRestoredState);

            return jobSubmitParam;
        }


    }
}
