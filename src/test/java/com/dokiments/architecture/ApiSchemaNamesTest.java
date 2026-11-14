package com.dokiments.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The API description names each request and response schema after its class's simple name, nested or not. Two web
 * records with the same simple name, in different modules, would silently share one schema in
 * {@code docs/openapi.yaml}, and one would describe the other wrongly. So every record in a web adapter needs a name of
 * its own.
 */
class ApiSchemaNamesTest {

    @Test
    void everyWebRecordHasANameOfItsOwn() {
        var records = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.dokiments").stream()
                        .filter(JavaClass::isRecord)
                        .filter(type -> type.getPackageName().contains(".adapter.in.web"))
                        .toList();

        Map<String, List<String>> shared = records.stream()
                .collect(Collectors.groupingBy(
                        JavaClass::getSimpleName, Collectors.mapping(JavaClass::getName, Collectors.toList())))
                .entrySet()
                .stream()
                .filter(name -> name.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(records).isNotEmpty();
        assertThat(shared).as("web records sharing a name").isEmpty();
    }
}
