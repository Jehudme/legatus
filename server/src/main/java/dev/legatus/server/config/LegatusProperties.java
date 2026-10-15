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

    public Storage getStorage() {
        return storage;
    }

    public Security getSecurity() {
        return security;
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
}
