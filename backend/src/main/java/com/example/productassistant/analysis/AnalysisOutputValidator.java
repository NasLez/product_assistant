package com.example.productassistant.analysis;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AnalysisOutputValidator {

    private static final int MAX_SCRIPT_CODE_POINTS = 150;

    public List<String> validate(ProductAnalysis analysis) {
        List<String> errors = new ArrayList<>();
        if (analysis == null) {
            return List.of("响应不是有效的分析对象");
        }
        validateStringList("targetUsers", analysis.targetUsers(), errors);
        validateStringList("useCases", analysis.useCases(), errors);
        validateStringList("painPoints", analysis.painPoints(), errors);

        if (analysis.coreSellingPoints().isEmpty()) {
            errors.add("coreSellingPoints 不能为空");
        } else {
            for (int index = 0; index < analysis.coreSellingPoints().size(); index++) {
                ProductAnalysis.SellingPoint point = analysis.coreSellingPoints().get(index);
                if (point == null || !StringUtils.hasText(point.claim())) {
                    errors.add("coreSellingPoints[" + index + "].claim 不能为空");
                }
                if (point == null || !StringUtils.hasText(point.evidence())) {
                    errors.add("coreSellingPoints[" + index + "].evidence 不能为空");
                }
            }
        }

        if (!StringUtils.hasText(analysis.videoScript())) {
            errors.add("videoScript 不能为空");
        } else {
            int count = analysis.videoScript().codePointCount(0, analysis.videoScript().length());
            if (count > MAX_SCRIPT_CODE_POINTS) {
                errors.add("videoScript 超过 150 个字符，当前为 " + count + " 个字符");
            }
        }
        return List.copyOf(errors);
    }

    private void validateStringList(String name, List<String> values, List<String> errors) {
        if (values == null || values.isEmpty()) {
            errors.add(name + " 不能为空");
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            if (!StringUtils.hasText(values.get(index))) {
                errors.add(name + "[" + index + "] 不能为空");
            }
        }
    }
}

