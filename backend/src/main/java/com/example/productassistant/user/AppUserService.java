package com.example.productassistant.user;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import com.example.productassistant.points.PointTransactionEntity;
import com.example.productassistant.points.PointTransactionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AppUserService {

    public static final int REGISTRATION_BONUS = 10;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PASSWORD_BYTES = 12;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AppUserMapper userMapper;
    private final PointTransactionMapper pointTransactionMapper;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(
            AppUserMapper userMapper,
            PointTransactionMapper pointTransactionMapper,
            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.pointTransactionMapper = pointTransactionMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUserEntity register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);
        if (userMapper.findByEmail(normalizedEmail) != null) {
            throw new EmailAlreadyRegisteredException();
        }

        AppUserEntity user = new AppUserEntity();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPoints(REGISTRATION_BONUS);
        user.setEnabled(true);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }

        PointTransactionEntity bonus = new PointTransactionEntity();
        bonus.setUserId(user.getId());
        bonus.setRequestId(UUID.randomUUID().toString());
        bonus.setTransactionType("REGISTER_BONUS");
        bonus.setStatus("SETTLED");
        bonus.setDelta(REGISTRATION_BONUS);
        bonus.setBalanceAfter(REGISTRATION_BONUS);
        bonus.setSettledAt(LocalDateTime.now(ZoneOffset.UTC));
        pointTransactionMapper.insert(bonus);
        return user;
    }

    public AppUserEntity findRequired(long userId) {
        AppUserEntity user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalStateException("Active user not found: " + userId);
        }
        return user;
    }

    public boolean isActive(long userId) {
        return Boolean.TRUE.equals(userMapper.findEnabled(userId));
    }

    public static String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new InvalidRegistrationException("请输入有效邮箱");
        }
        String normalized = Normalizer.normalize(email.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidRegistrationException("请输入有效邮箱");
        }
        return normalized;
    }

    public static void validatePassword(String password) {
        if (password == null) {
            throw new InvalidRegistrationException("密码长度必须为 12 到 72 个 UTF-8 字节");
        }
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MIN_PASSWORD_BYTES || byteLength > MAX_PASSWORD_BYTES) {
            throw new InvalidRegistrationException("密码长度必须为 12 到 72 个 UTF-8 字节");
        }
    }
}
