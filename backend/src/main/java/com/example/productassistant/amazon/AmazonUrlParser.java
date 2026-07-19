package com.example.productassistant.amazon;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AmazonUrlParser {

    private static final Pattern PRODUCT_PATH = Pattern.compile(
            "/(?:dp|gp/product|product)/([A-Z0-9]{10})(?:[/]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IPV4 = Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "amazon.com", "amazon.ca", "amazon.com.mx", "amazon.com.br",
            "amazon.co.uk", "amazon.de", "amazon.fr", "amazon.it", "amazon.es",
            "amazon.nl", "amazon.se", "amazon.pl", "amazon.com.be",
            "amazon.co.jp", "amazon.in", "amazon.com.au", "amazon.sg",
            "amazon.ae", "amazon.sa", "amazon.com.tr", "amazon.eg");

    public AmazonProductKey parse(String value) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidAmazonUrlException("Amazon 商品链接不能为空");
        }

        final URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException exception) {
            throw new InvalidAmazonUrlException("Amazon 商品链接格式无效");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidAmazonUrlException("Amazon 商品链接必须使用 HTTPS");
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidAmazonUrlException("Amazon 商品链接不能包含用户凭据");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new InvalidAmazonUrlException("Amazon 商品链接不能使用非标准端口");
        }

        String host = normalizeHost(uri.getHost());
        if (isIpAddress(host) || !ALLOWED_DOMAINS.contains(host)) {
            throw new InvalidAmazonUrlException("暂不支持该 Amazon 站点");
        }

        String path = uri.getPath() == null ? "" : uri.getPath();
        Matcher matcher = PRODUCT_PATH.matcher(path);
        if (!matcher.find()) {
            throw new InvalidAmazonUrlException("无法从链接中解析 ASIN");
        }

        return new AmazonProductKey(host, matcher.group(1).toUpperCase(Locale.ROOT), uri.toString());
    }

    private String normalizeHost(String host) {
        if (!StringUtils.hasText(host)) {
            throw new InvalidAmazonUrlException("Amazon 商品链接缺少域名");
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }

    private boolean isIpAddress(String host) {
        return IPV4.matcher(host).matches() || host.contains(":") || "localhost".equals(host);
    }
}

