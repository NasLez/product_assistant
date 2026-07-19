package com.example.productassistant.amazon;

public record AmazonProductKey(String amazonDomain, String asin, String sourceUrl) {

    public String cacheKey() {
        return amazonDomain + ":" + asin;
    }
}

