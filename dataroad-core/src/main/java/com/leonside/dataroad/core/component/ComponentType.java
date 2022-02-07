package com.leonside.dataroad.core.component;

import com.leonside.dataroad.common.spi.ItemProcessor;
import com.leonside.dataroad.common.spi.ItemReader;
import com.leonside.dataroad.common.spi.ItemUnionProcessor;
import com.leonside.dataroad.common.spi.ItemWriter;
import com.leonside.dataroad.core.spi.ItemAggregationProcessor;
import com.leonside.dataroad.core.spi.ItemDeciderProcessor;
import com.leonside.dataroad.core.spi.ItemLookupProcessor;
import com.leonside.dataroad.core.spi.JobPredicate;

public enum ComponentType{

        reader("reader", ItemReader.class),
        writer("writer", ItemWriter.class),
        processor("processor", ItemProcessor.class),
//        join("join", ItemJ.class),
        lookup("lookup", ItemLookupProcessor.class),
        agg("agg", ItemAggregationProcessor.class),
        decider("decider", ItemDeciderProcessor.class),
        deciderOn("deciderOn", JobPredicate.class),
        deciderEnd("deciderEnd", ItemDeciderProcessor.class),
        union("union", ItemUnionProcessor.class);
        private String name;
        private Class spi;

        ComponentType(String name, Class spi) {
                this.name = name;
                this.spi = spi;
        }

        public String getName() {
                return name;
        }

        public void setName(String name) {
                this.name = name;
        }

        public Class getSpi() {
                return spi;
        }

        public void setSpi(Class spi) {
                this.spi = spi;
        }

}