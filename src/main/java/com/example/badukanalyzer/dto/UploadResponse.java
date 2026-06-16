package com.example.badukanalyzer.dto;

public class UploadResponse {

    private final String filename;
    private final String sgfContent;

    private UploadResponse(String filename, String sgfContent) {
        this.filename = filename;
        this.sgfContent = sgfContent;
    }

    public static UploadResponseBuilder builder() {
        return new UploadResponseBuilder();
    }

    public String getFilename() {
        return filename;
    }

    public String getSgfContent() {
        return sgfContent;
    }

    public static class UploadResponseBuilder {
        private String filename;
        private String sgfContent;

        UploadResponseBuilder() {}

        public UploadResponseBuilder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public UploadResponseBuilder sgfContent(String sgfContent) {
            this.sgfContent = sgfContent;
            return this;
        }

        public UploadResponse build() {
            return new UploadResponse(filename, sgfContent);
        }
    }
}
