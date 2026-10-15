package dev.legatus.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Legatus orchestration broker.
 * <p>
 * Configured in {@code application.yml} under the {@code legatus} prefix.
 */
@ConfigurationProperties(prefix = "legatus", ignoreUnknownFields = true)
public class LegatusProperties {

    private final Storage storage = new Storage();
    private final Security security = new Security();
    private final Scheduler scheduler = new Scheduler();
    private final Features features = new Features();
    private final Cron cron = new Cron();
    private final Webhooks webhooks = new Webhooks();
    private final Retention retention = new Retention();

    public Storage getStorage() {
        return storage;
    }

    public Security getSecurity() {
        return security;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public Features getFeatures() {
        return features;
    }

    public Cron getCron() {
        return cron;
    }

    public Webhooks getWebhooks() {
        return webhooks;
    }

    public Retention getRetention() {
        return retention;
    }

    public static class Storage {

        private String bytecodeDir = "./target/bytecode";
        private String dbDir = "./target/h2db/db";

        public String getBytecodeDir() {
            return bytecodeDir;
        }

        public void setBytecodeDir(String bytecodeDir) {
            this.bytecodeDir = bytecodeDir;
        }

        public String getDbDir() {
            return dbDir;
        }

        public void setDbDir(String dbDir) {
            this.dbDir = dbDir;
        }
    }

    public static class Security {

        private final Argon2 argon2 = new Argon2();
        private final RateLimit rateLimit = new RateLimit();
        private String masterKey;

        public Argon2 getArgon2() {
            return argon2;
        }

        public RateLimit getRateLimit() {
            return rateLimit;
        }

        public String getMasterKey() {
            return masterKey;
        }

        public void setMasterKey(String masterKey) {
            this.masterKey = masterKey;
        }

        public static class Argon2 {

            private int memoryCostKb = 65536; // 64 MB
            private int iterations = 3;
            private int parallelism = 4;
            private int poolSize = 4;

            public int getMemoryCostKb() {
                return memoryCostKb;
            }

            public void setMemoryCostKb(int memoryCostKb) {
                this.memoryCostKb = memoryCostKb;
            }

            public int getIterations() {
                return iterations;
            }

            public void setIterations(int iterations) {
                this.iterations = iterations;
            }

            public int getParallelism() {
                return parallelism;
            }

            public void setParallelism(int parallelism) {
                this.parallelism = parallelism;
            }

            public int getPoolSize() {
                return poolSize;
            }

            public void setPoolSize(int poolSize) {
                this.poolSize = poolSize;
            }
        }

        public static class RateLimit {

            private int registrationPerMinute = 5;

            public int getRegistrationPerMinute() {
                return registrationPerMinute;
            }

            public void setRegistrationPerMinute(int registrationPerMinute) {
                this.registrationPerMinute = registrationPerMinute;
            }
        }
    }

    public static class Scheduler {

        private String strategy = "LEAST_LOADED";
        private int dispatchBatchSize = 64;
        private int maxQueueDepth = 100000;
        private int defaultMaxRetries = 2;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public int getDispatchBatchSize() {
            return dispatchBatchSize;
        }

        public void setDispatchBatchSize(int dispatchBatchSize) {
            this.dispatchBatchSize = dispatchBatchSize;
        }

        public int getMaxQueueDepth() {
            return maxQueueDepth;
        }

        public void setMaxQueueDepth(int maxQueueDepth) {
            this.maxQueueDepth = maxQueueDepth;
        }

        public int getDefaultMaxRetries() {
            return defaultMaxRetries;
        }

        public void setDefaultMaxRetries(int defaultMaxRetries) {
            this.defaultMaxRetries = defaultMaxRetries;
        }
    }

    public static class Features {

        private boolean cronScheduler = true;
        private boolean webhooks = true;
        private boolean bytecodeCache = true;
        private boolean workflows = false;

        public boolean isCronScheduler() {
            return cronScheduler;
        }

        public void setCronScheduler(boolean cronScheduler) {
            this.cronScheduler = cronScheduler;
        }

        public boolean isWebhooks() {
            return webhooks;
        }

        public void setWebhooks(boolean webhooks) {
            this.webhooks = webhooks;
        }

        public boolean isBytecodeCache() {
            return bytecodeCache;
        }

        public void setBytecodeCache(boolean bytecodeCache) {
            this.bytecodeCache = bytecodeCache;
        }

        public boolean isWorkflows() {
            return workflows;
        }

        public void setWorkflows(boolean workflows) {
            this.workflows = workflows;
        }
    }

    public static class Cron {

        private boolean enabled = true;
        private java.util.List<CronJobConfig> jobs = new java.util.ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public java.util.List<CronJobConfig> getJobs() {
            return jobs;
        }

        public void setJobs(java.util.List<CronJobConfig> jobs) {
            this.jobs = jobs;
        }
    }

    public static class CronJobConfig {

        private String name;
        private String functionName;
        private String cronExpression;
        private dev.legatus.server.domain.enumeration.TaskPriority priority = dev.legatus.server.domain.enumeration.TaskPriority.NORMAL;
        private java.util.Map<String, Object> arguments = new java.util.HashMap<>();
        private java.util.Map<String, String> env = new java.util.HashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFunctionName() {
            return functionName;
        }

        public void setFunctionName(String functionName) {
            this.functionName = functionName;
        }

        public String getCronExpression() {
            return cronExpression;
        }

        public void setCronExpression(String cronExpression) {
            this.cronExpression = cronExpression;
        }

        public dev.legatus.server.domain.enumeration.TaskPriority getPriority() {
            return priority;
        }

        public void setPriority(dev.legatus.server.domain.enumeration.TaskPriority priority) {
            this.priority = priority;
        }

        public java.util.Map<String, Object> getArguments() {
            return arguments;
        }

        public void setArguments(java.util.Map<String, Object> arguments) {
            this.arguments = arguments;
        }

        public java.util.Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(java.util.Map<String, String> env) {
            this.env = env;
        }
    }

    public static class Webhooks {

        private boolean enabled = true;
        private String secret;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Retention {

        private boolean autoPurge = true;
        private int completedTaskTtlHours = 168;

        public boolean isAutoPurge() {
            return autoPurge;
        }

        public void setAutoPurge(boolean autoPurge) {
            this.autoPurge = autoPurge;
        }

        public int getCompletedTaskTtlHours() {
            return completedTaskTtlHours;
        }

        public void setCompletedTaskTtlHours(int completedTaskTtlHours) {
            this.completedTaskTtlHours = completedTaskTtlHours;
        }
    }
}
