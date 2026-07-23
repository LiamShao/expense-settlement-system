package com.example.expense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app.receipt")
public class ReceiptFileProperties {

    private final Storage storage = new Storage();
    private final Scanner scanner = new Scanner();

    public Storage getStorage() {
        return storage;
    }

    public Scanner getScanner() {
        return scanner;
    }

    public static class Storage {

        private String type = "unavailable";
        private Path localRoot = Path.of(".local", "receipts");
        private String s3Bucket;
        private String s3Region = "ap-northeast-1";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Path getLocalRoot() {
            return localRoot;
        }

        public void setLocalRoot(Path localRoot) {
            this.localRoot = localRoot;
        }

        public String getS3Bucket() {
            return s3Bucket;
        }

        public void setS3Bucket(String s3Bucket) {
            this.s3Bucket = s3Bucket;
        }

        public String getS3Region() {
            return s3Region;
        }

        public void setS3Region(String s3Region) {
            this.s3Region = s3Region;
        }
    }

    public static class Scanner {

        private String type = "unavailable";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
