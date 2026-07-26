package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record DuplicateTestId(String testId, List<Method> methods, List<String> holderClassNames) {

    public DuplicateTestId(String testId, List<Method> methods) {
        this(testId, methods, holderClassNames(methods));
    }

    private static List<String> holderClassNames(List<Method> methods) {
        Set<String> names = new LinkedHashSet<>();
        for (Method method : methods) {
            if (method != null) {
                names.add(
                    method.getDeclaringClass()
                        .getName());
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(names));
    }
}
