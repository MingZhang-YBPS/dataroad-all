package com.leonside.dataroad.config.job;

import com.google.common.collect.Sets;
import com.leonside.dataroad.common.context.ExecuteContext;
import com.leonside.dataroad.common.context.JobSetting;
import com.leonside.dataroad.common.exception.JobFlowException;
import com.leonside.dataroad.common.extension.ExtensionLoader;
import com.leonside.dataroad.common.spi.JobExecutionListener;
import com.leonside.dataroad.config.ComponentFactory;
import com.leonside.dataroad.config.JobCreator;
import com.leonside.dataroad.config.JobSchemaParser;
import com.leonside.dataroad.common.config.Options;
import com.leonside.dataroad.config.domain.GenericComponentConfig;
import com.leonside.dataroad.config.domain.JobConfig;
import com.leonside.dataroad.config.domain.JobConfigs;
import com.leonside.dataroad.core.Job;
import com.leonside.dataroad.core.builder.JobBuilder;
import com.leonside.dataroad.core.builder.JobFlowBuilder;
import com.leonside.dataroad.core.builder.MultiJobFlowBuilder;
import com.leonside.dataroad.core.flow.JobFlow;
import com.leonside.dataroad.core.predicate.OtherwisePredicate;
import com.leonside.dataroad.core.spi.JobEngineProvider;
import com.leonside.dataroad.core.spi.JobPredicate;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;

import java.util.*;

/**
 * @author leon
 */
public class JsonJobCreator implements JobCreator {

    private JobSchemaParser jobSchemaParser;

    private Options options;

    public JsonJobCreator(JobSchemaParser jobSchemaParser, Options options) {
        this.jobSchemaParser = jobSchemaParser;
        this.options = options;
    }

    @Override
    public List<Job> createJob() throws Exception {
        return createJob(jobSchemaParser.parserJSONPath(options.getConf()));
    }

    private List<Job> createJob(JobConfigs jobs) throws Exception {
        JobEngineProvider jobEngineProvider = ExtensionLoader.getExtensionLoader(JobEngineProvider.class).getFirstExtension();

        JobConfig job = jobs.getJob();

        if(CollectionUtils.isEmpty(job.getContent())){
            throw new JobFlowException("job content can not be null.");
        }

        if(job.getSetting() == null){
            job.setSetting(new JobSetting());
        }

        //构建Job节点的上下级树形关系
        job.buildJobFlowRelation();

        List<Job> jobList = new ArrayList<>();

        for (Map<String, GenericComponentConfig> componentConfigMap : job.getContent()) {
            GenericComponentConfig startComponentConfig = componentConfigMap.values().iterator().next();

            ExecuteContext executeContext = jobEngineProvider.createExecuteContext(job.getSetting(),job.getAllComponents(), options);
            JobBuilder jobBuilder = JobBuilder.newInstance().listener(new JobExecutionListener() {
            }).executeContext(executeContext); //todo
            JobFlowBuilder jobFlowBuilder = jobBuilder.reader(ComponentFactory.getComponent(executeContext, startComponentConfig));

            MultiJobFlowBuilder currentDecider = null;

            doCreateMainFlow(executeContext, jobFlowBuilder, currentDecider, startComponentConfig.getChilds());

            Job buildJob = jobFlowBuilder.build();

            jobList.add(buildJob);
        }

        return jobList;
    }

    private void doCreateMainFlow(ExecuteContext executeContext, JobFlowBuilder jobFlowBuilder, MultiJobFlowBuilder currentDecider, Set<GenericComponentConfig> genericComponentConfigs) {
       //判断是否存在分支
        if(genericComponentConfigs.size() >= 2){
            GenericComponentConfig lastDeciderFlow = (GenericComponentConfig) genericComponentConfigs.toArray()[genericComponentConfigs.size() - 1];
            lastDeciderFlow.markCurrentDeciderLastFlow();
        }
        //延迟执行的component，用于解决分支中的union逻辑
        LinkedHashSet<GenericComponentConfig> lazyCreateComponent = Sets.newLinkedHashSet();

        for (GenericComponentConfig child : genericComponentConfigs) {
            switch (child.getType()){
                case reader:
                    jobFlowBuilder.reader(ComponentFactory.getComponent(executeContext, child));
                    if(CollectionUtils.isNotEmpty(child.getChilds())){
                        doCreateMainFlow(executeContext,jobFlowBuilder, currentDecider, child.getChilds());
                    }
                    break;
                case writer:
                    jobFlowBuilder.writer(ComponentFactory.getComponent(executeContext, child));
                    if(CollectionUtils.isNotEmpty(child.getChilds())){
                        doCreateMainFlow(executeContext, jobFlowBuilder, currentDecider, child.getChilds());
                    }
                    break;
                case processor:
                case agg:
                case lookup:
                    jobFlowBuilder.processor(ComponentFactory.getComponent(executeContext,child));
                    if(CollectionUtils.isNotEmpty(child.getChilds())){
                        doCreateMainFlow(executeContext,jobFlowBuilder, currentDecider, child.getChilds());
                    }
                    break;
                case deciderOn:
                    if(currentDecider == null){
                        currentDecider = jobFlowBuilder.decider();
                    }
                    JobPredicate jobPredicate = ComponentFactory.getJobPredicate(executeContext,child);
                    if(jobPredicate instanceof OtherwisePredicate){
                        currentDecider.otherwise();
                    }else{
                        currentDecider.on(jobPredicate);
                    }
                    doCreateDeciderFlow(executeContext,currentDecider, child, lazyCreateComponent);

                    break;
                case union:
                    //根据指定的dependencies进行union
                    if(ArrayUtils.isNotEmpty(child.getDependencies())){
                        List<Integer> unionFlowIndex = new ArrayList<>();
                        int i = 0;
                        for (JobFlow jobFlow: currentDecider.getJobFlowDeciders().values()) {
                            if(ArrayUtils.contains(child.getDependencies(), jobFlow.getTask().getComponentName())){
                                unionFlowIndex.add(i);
                            }
                            i++;
                        }
                        jobFlowBuilder.union(unionFlowIndex.toArray(new Integer[]{}));
                    }else{
                        jobFlowBuilder.union();
                    }
                    if(CollectionUtils.isNotEmpty(child.getChilds())){
                        doCreateMainFlow(executeContext,jobFlowBuilder, currentDecider, child.getChilds());
                    }
                    break;
//                case join:
                default:
                    break;
            }
        }

    }

    private void doCreateDeciderFlow(ExecuteContext executeContext, MultiJobFlowBuilder currentDecider, GenericComponentConfig genericComponentConfig, Set<GenericComponentConfig> lazyCreateComponent) {
        for (GenericComponentConfig child : genericComponentConfig.getChilds()) {
            switch (child.getType()){
                case writer:
                    currentDecider.writer(ComponentFactory.getComponent(executeContext,child));
                    if(CollectionUtils.isNotEmpty(child.getChilds())){
                        doCreateDeciderFlow(executeContext,currentDecider, child,lazyCreateComponent);
                    }
                    break;
                case processor:
                case agg:
                case lookup:
                    currentDecider.processor(ComponentFactory.getComponent(executeContext,child));
                    if(CollectionUtils.isNotEmpty(child.getChilds())){
                        doCreateDeciderFlow(executeContext, currentDecider, child,lazyCreateComponent);
                    }
                    break;
                case union:
                    lazyCreateComponent.add(child);
                    break;
                default:
                    break;
            }
            if(child.isDeciderLastFlow()){
                JobFlowBuilder jobFlowBuilder = currentDecider.getJobFlowBuilder();
                processDeciderEnd(currentDecider);

                lazyCreateComponent.addAll(child.getChilds());

                doCreateMainFlow(executeContext,jobFlowBuilder, currentDecider,lazyCreateComponent);
            }

        }

    }

    private void processDeciderEnd(MultiJobFlowBuilder currentDecider) {
        //进行分流end处理,并将其设置为null.
        if(currentDecider != null){
            currentDecider.end();
            currentDecider = null;
        }
    }

}
