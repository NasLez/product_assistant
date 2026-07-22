package com.example.productassistant.crypto;

import java.util.ArrayList;
import java.util.List;

import com.example.productassistant.analysis.AnalysisApplicationService;
import com.example.productassistant.analysis.AnalysisResultEntity;
import com.example.productassistant.analysis.AnalysisResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class VideoScriptBackfillService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VideoScriptBackfillService.class);

    private final VideoScriptEncryptionProperties properties;
    private final VideoScriptCipher cipher;
    private final AnalysisResultMapper analysisMapper;
    private final TransactionTemplate transactionTemplate;

    public VideoScriptBackfillService(
            VideoScriptEncryptionProperties properties,
            VideoScriptCipher cipher,
            AnalysisResultMapper analysisMapper,
            TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.cipher = cipher;
        this.analysisMapper = analysisMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isBackfillEnabled()) {
            return;
        }
        int batchSize = properties.getBackfillBatchSize();
        if (batchSize < 1 || batchSize > 1_000) {
            throw new EncryptionConfigurationException(
                    "Video-script backfill batch size must be between 1 and 1000");
        }

        long afterId = 0;
        long migrated = 0;
        while (true) {
            List<AnalysisResultEntity> rows = analysisMapper.findPlaintextBatch(afterId, batchSize);
            if (rows.isEmpty()) {
                break;
            }
            afterId = rows.get(rows.size() - 1).getId();
            List<BackfillValue> encryptedRows = new ArrayList<>(rows.size());
            for (AnalysisResultEntity row : rows) {
                try {
                    String keyVersion = properties.getActiveKeyVersion().trim();
                    EncryptedText encrypted = cipher.encrypt(
                            row.getVideoScript(),
                            AnalysisApplicationService.associatedData(
                                    row.getProductSnapshotId(),
                                    row.getModel(),
                                    row.getPromptVersion(),
                                    keyVersion));
                    encryptedRows.add(new BackfillValue(row.getId(), encrypted));
                } catch (RuntimeException exception) {
                    log.error("Video-script encryption backfill failed for analysisId={}", row.getId());
                }
            }
            Integer updated = transactionTemplate.execute(status -> {
                int count = 0;
                for (BackfillValue row : encryptedRows) {
                    count += analysisMapper.storeEncryptedVideoScript(
                            row.id(),
                            row.encrypted().ciphertext(),
                            row.encrypted().iv(),
                            row.encrypted().keyVersion());
                }
                return count;
            });
            migrated += updated == null ? 0 : updated;
        }
        log.info("Video-script encryption backfill completed; migratedCount={}", migrated);
    }

    private record BackfillValue(long id, EncryptedText encrypted) {
    }
}
