package com.example.productassistant.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.video-script-encryption")
public class VideoScriptEncryptionProperties {

    private String activeKeyVersion = "v1";
    private String keys = "";
    private boolean backfillEnabled;
    private int backfillBatchSize = 100;

    public String getActiveKeyVersion() {
        return activeKeyVersion;
    }

    public void setActiveKeyVersion(String activeKeyVersion) {
        this.activeKeyVersion = activeKeyVersion;
    }

    public String getKeys() {
        return keys;
    }

    public void setKeys(String keys) {
        this.keys = keys;
    }

    public boolean isBackfillEnabled() {
        return backfillEnabled;
    }

    public void setBackfillEnabled(boolean backfillEnabled) {
        this.backfillEnabled = backfillEnabled;
    }

    public int getBackfillBatchSize() {
        return backfillBatchSize;
    }

    public void setBackfillBatchSize(int backfillBatchSize) {
        this.backfillBatchSize = backfillBatchSize;
    }
}
