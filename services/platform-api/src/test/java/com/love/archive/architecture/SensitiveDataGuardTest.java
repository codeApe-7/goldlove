package com.love.archive.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SensitiveDataGuardTest {

    private static final Path MODULE_ROOT = Path.of("").toAbsolutePath().normalize();

    @Test
    void repositoryContainsNoMapperXmlOrSpringSecurityUsage() throws IOException {
        List<Path> mapperXml;
        try (var files = Files.walk(MODULE_ROOT.resolve("src"))) {
            mapperXml = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .toList();
        }
        assertThat(mapperXml).isEmpty();

        String productionSources = readTree(MODULE_ROOT.resolve("src/main"));
        String modulePom = Files.readString(MODULE_ROOT.resolve("pom.xml"));
        String parentPom = Files.readString(MODULE_ROOT.resolve("../../pom.xml").normalize());
        assertThat(productionSources).doesNotContain("org.springframework.security");
        assertThat(productionSources)
                .doesNotContain("lombok.Data")
                .doesNotContain("lombok.ToString")
                .doesNotContain("@Data")
                .doesNotContain("@ToString");
        assertThat(modulePom + parentPom).doesNotContain("spring-security");
    }

    @Test
    void productionConfigHasNoPlaintextSecretDefaults() throws IOException {
        String applicationConfig = Files.readString(MODULE_ROOT.resolve("src/main/resources/application.yml"));
        assertThat(applicationConfig)
                .contains("${DB_PASSWORD:}")
                .contains("${REDIS_PASSWORD:}")
                .contains("${ADMIN_BOOTSTRAP_PASSWORD:}")
                .contains("${COS_SECRET_KEY:}")
                .contains("${XPAY_MERCHANT_PRIVATE_KEY:}")
                .contains("${XPAY_PLATFORM_PUBLIC_KEY:}");
    }

    /**
     * 手机号、微信号、抖音号现在是明文列，所以「不要打进日志」这条比以前更重要。
     */
    @Test
    void productionLoggingDoesNotReferenceSensitiveRequestFields() throws IOException {
        String productionJava = readTree(MODULE_ROOT.resolve("src/main/java"));
        assertThat(productionJava)
                .doesNotContainPattern("(?i)LOGGER\\.(trace|debug|info|warn|error)\\("
                        + "[^;]*(password|phone|credential|activationCode|activation_code"
                        + "|wechat_?id|douyin|clientIp|sessionReference|objectKey|object_key)");
    }

    @Test
    void localEnvExampleCarriesNoRealCredentials() throws IOException {
        String envExample = Files.readString(
                MODULE_ROOT.resolve("../../.env.example").normalize());
        assertThat(envExample)
                .contains("XPAY_MERCHANT_PRIVATE_KEY=")
                .contains("XPAY_PLATFORM_PUBLIC_KEY=")
                .contains("COS_SECRET_KEY=");
        // 示例文件绝不带真实密钥，也不预置商户号。
        assertThat(envExample)
                .doesNotContain("BEGIN PRIVATE KEY")
                .doesNotContainPattern("XPAY_PID=\\d");
    }

    @Test
    void everyCustomMapperDeclaresAnnotatedStatements() throws IOException {
        List<Path> mappers;
        try (var files = Files.walk(MODULE_ROOT.resolve("src/main/java"))) {
            mappers = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Mapper.java"))
                    .toList();
        }
        assertThat(mappers).isNotEmpty();
        for (Path mapper : mappers) {
            String source = Files.readString(mapper);
            String body = source.substring(source.indexOf('{'), source.lastIndexOf('}'));
            if (!body.contains(";")) {
                continue;
            }
            assertThat(source)
                    .withFailMessage("Mapper %s declares custom statements without annotations",
                            mapper)
                    .containsAnyOf(
                            "@Select", "@SelectProvider", "@Insert", "@Update", "@Delete");
        }
    }

    @Test
    void sourceAndResourceTreesContainNoMapperXml() throws IOException {
        List<Path> xmlFiles;
        try (var files = Files.walk(MODULE_ROOT.resolve("src"))) {
            xmlFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".xml"))
                    .toList();
        }
        assertThat(xmlFiles).isEmpty();

        String modulePom = Files.readString(MODULE_ROOT.resolve("pom.xml"));
        assertThat(modulePom)
                .doesNotContain("mapper-locations")
                .doesNotContain("Mapper.xml");
    }

    private static String readTree(Path root) throws IOException {
        StringBuilder content = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                content.append(Files.readString(file)).append('\n');
            }
        }
        return content.toString();
    }
}
