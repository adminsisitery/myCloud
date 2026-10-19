package com.cloudrive.pan_common.constant;


public class ESConstants {

    public static class QUEUE {
        public static final String INSERT_QUEUE = "ES.insert.queue";
        public static final String DELETE_QUEUE = "ES.delete.queue";
        public static final String UPDATE_QUEUE = "ES.update.queue";
    }

    public static class EXCHANGE{
        public static final String TOPIC_EXCHANGE = "ES.topic";
    }

    public static class KEY{
        public static final String INSERT_KEY = "ES_insert";
        public static final String DELETE_KEY = "ES_delete";
        public static final String UPDATE_KEY = "ES_update";
    }


}
