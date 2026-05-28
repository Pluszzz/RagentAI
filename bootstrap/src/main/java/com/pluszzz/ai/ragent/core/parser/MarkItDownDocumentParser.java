Copyright 2026 Pluszzz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
package com.pluszzz.ai.ragent.core.parser;

import com.pluszzz.ai.ragent.framework.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MarkItDown 文档解析器
 * <p>
 * 通过 ProcessBuilder 调用 Python markitdown 子进程，将 Office 文档和 PDF 转换为结构化 Markdown 输出。
 * 输出保留标题、表格、列表等语义结构，更适合 RAG 场景下的分块和向量化。
 * </p>
 */
@Slf4j
@Component
public class MarkItDownDocumentParser implements DocumentParser {

    private static final Map<String, String> MIME_TO_EXT = Map.ofEntries(
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.ms-powerpoint", "ppt"),
            Map.entry("text/html", "html"),
            Map.entry("application/xhtml+xml", "html"),
            Map.entry("image/png", "png"),
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/gif", "gif"),
            Map.entry("image/bmp", "bmp"),
            Map.entry("image/webp", "webp")
    );

    private final MarkItDownProperties properties;

    private volatile Boolean available;
    private volatile long lastCheckTime;
    private static final long CHECK_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    public MarkItDownDocumentParser(MarkItDownProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getParserType() {
        return ParserType.MARKITDOWN.getType();
    }

    @Override
    public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            return ParseResult.ofText("");
        }

        String ext = resolveExtension(mimeType, options);
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("markitdown-", "." + ext);
            Files.write(tempFile, content);

            String markdown = invokeMarkItDown(tempFile);
            return ParseResult.of(markdown, Map.of("parser_strategy", "markitdown"));
        } catch (MarkItDownException e) {
            throw e;
        } catch (IOException e) {
            log.error("MarkItDown 临时文件操作失败", e);
            throw new MarkItDownException("临时文件操作失败: " + e.getMessage(), e);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    @Override
    public String extractText(InputStream stream, String fileName) {
        try {
            String ext = resolveExtFromFileName(fileName);
            Path tempFile = Files.createTempFile("markitdown-", "." + ext);
            try {
                Files.write(tempFile, stream.readAllBytes());
                return invokeMarkItDown(tempFile);
            } finally {
                deleteTempFile(tempFile);
            }
        } catch (MarkItDownException e) {
            throw e;
        } catch (IOException e) {
            log.error("MarkItDown 提取文本失败: {}", fileName, e);
            throw new ServiceException("MarkItDown 解析文件失败: " + fileName);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String lower = mimeType.toLowerCase();
        return lower.contains("pdf")
                || lower.contains("word")
                || lower.contains("msword")
                || lower.contains("excel")
                || lower.contains("spreadsheet")
                || lower.contains("powerpoint")
                || lower.contains("presentation")
                || lower.contains("html")
                || lower.startsWith("image/");
    }

    /**
     * 检查 MarkItDown 是否可用（带 5 分钟缓存）
     */
    public boolean isAvailable() {
        if (!properties.enabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (available != null && (now - lastCheckTime) < CHECK_TTL_MS) {
            return available;
        }
        synchronized (this) {
            if (available != null && (now - lastCheckTime) < CHECK_TTL_MS) {
                return available;
            }
            available = checkAvailable();
            lastCheckTime = System.currentTimeMillis();
            return available;
        }
    }

    private boolean checkAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    properties.pythonPath(), "-c", "import markitdown");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("MarkItDown 可用性检查失败: {}", e.getMessage());
            return false;
        }
    }

    private String invokeMarkItDown(Path tempFile) {
        ProcessBuilder pb = new ProcessBuilder(
                properties.pythonPath(), "-m", "markitdown", tempFile.toString());
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();

            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
                try (InputStream is = process.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
                try (InputStream is = process.getErrorStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            boolean finished = process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new MarkItDownException("MarkItDown 执行超时 ("
                        + properties.timeoutSeconds() + "s)");
            }

            int exitCode = process.exitValue();
            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();

            if (exitCode != 0) {
                log.warn("MarkItDown 非零退出码 {}: {}", exitCode, stderr);
                throw new MarkItDownException("MarkItDown exited with code " + exitCode + ": " + stderr);
            }

            String trimmed = stdout != null ? stdout.trim() : "";
            if (trimmed.isEmpty() && !stderr.isEmpty()) {
                log.warn("MarkItDown 无标准输出，stderr: {}", stderr);
            }

            return trimmed;
        } catch (MarkItDownException e) {
            throw e;
        } catch (Exception e) {
            log.error("MarkItDown 子进程执行失败", e);
            throw new MarkItDownException("MarkItDown 子进程执行失败: " + e.getMessage(), e);
        }
    }

    private String resolveExtension(String mimeType, Map<String, Object> options) {
        if (options != null && options.containsKey("fileName")) {
            String ext = resolveExtFromFileName((String) options.get("fileName"));
            if (!"tmp".equals(ext)) {
                return ext;
            }
        }
        if (mimeType != null) {
            String lower = mimeType.toLowerCase();
            // try exact match first
            if (MIME_TO_EXT.containsKey(lower)) {
                return MIME_TO_EXT.get(lower);
            }
            // fuzzy match
            if (lower.contains("pdf")) return "pdf";
            if (lower.contains("word") || lower.contains("msword")) return "docx";
            if (lower.contains("excel") || lower.contains("spreadsheet")) return "xlsx";
            if (lower.contains("powerpoint") || lower.contains("presentation")) return "pptx";
            if (lower.contains("html")) return "html";
            if (lower.startsWith("image/")) {
                String sub = lower.substring(6);
                if (sub.equals("jpeg")) return "jpg";
                return sub;
            }
        }
        return "tmp";
    }

    private String resolveExtFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "tmp";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "tmp";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("删除临时文件失败: {}", tempFile, e);
            }
        }
    }
}
