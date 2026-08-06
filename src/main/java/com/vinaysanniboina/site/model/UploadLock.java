package com.vinaysanniboina.site.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code content/uploads.yaml} — the machine-written record of every binary pushed to Cloudflare R2.
 *
 * <p>Keyed by the local file path relative to the repo root, e.g.
 * {@code files/front-wing/front-wing-assembly.step}. The {@code upload} command writes this file;
 * the generator reads it to fill in size, checksum and download URL on project pages. Keeping it
 * separate from the project YAML means the uploader never has to rewrite a hand-edited file (which
 * would destroy comments and formatting), and a re-upload shows up as a clean one-line diff.
 */
public record UploadLock(Map<String, Entry> files) {

    public Map<String, Entry> fileMap() {
        return files == null ? Map.of() : files;
    }

    public static UploadLock empty() {
        return new UploadLock(new LinkedHashMap<>());
    }

    public record Entry(
            String key,
            long size,
            String sha256,
            String contentType,
            String uploadedAt,
            String bucket
    ) {}
}
