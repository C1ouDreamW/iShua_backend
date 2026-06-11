package cn.heycloudream.ishua_backend.service.file.impl;

import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.service.file.FileStorageService;
import cn.heycloudream.ishua_backend.util.TaskIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件存储实现。
 * <p>
 * 文件存储于 {@code {base-dir}/} 目录下，以 UUID 命名保留原始扩展名。
 * </p>
 *
 * @author C1ouD
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final String FILE_URL_PREFIX = "file://";

    private final Path baseDir;

    public LocalFileStorageService(@Value("${ishua.filestorage.local-dir:/tmp/atlas/upload}") String localDir) {
        this.baseDir = Paths.get(localDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
            log.info("[文件存储] 本地存储目录已就绪: {}", this.baseDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建文件存储目录: " + this.baseDir, e);
        }
    }

    @Override
    public String store(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        String storedName = TaskIdGenerator.generate() + "." + extension;
        Path targetPath = baseDir.resolve(storedName);

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("[文件存储] 文件已落盘: {} → {}", originalFilename, targetPath);

        // 统一使用正斜杠输出 file:// 路径，避免 Windows 反斜杠在 Python 端 unquote 时被误解
        String normalizedPath = targetPath.toString().replace('\\', '/');
        return FILE_URL_PREFIX + normalizedPath;
    }

    @Override
    public Path resolvePath(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(FILE_URL_PREFIX)) {
            throw new BusinessException(400, "无效的文件标识: " + fileUrl);
        }
        return Paths.get(fileUrl.substring(FILE_URL_PREFIX.length()));
    }

    @Override
    public void delete(String fileUrl) {
        Path path = resolvePath(fileUrl);
        try {
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.info("[文件存储] 文件已删除: {}", path);
            }
        } catch (IOException e) {
            log.warn("[文件存储] 删除文件失败: {}", path, e);
        }
    }

    private static String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "tmp";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "tmp";
        }
        return filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
