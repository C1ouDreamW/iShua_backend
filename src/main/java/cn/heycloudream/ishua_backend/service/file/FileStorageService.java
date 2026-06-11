package cn.heycloudream.ishua_backend.service.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文件存储服务抽象。
 * <p>
 * 当前提供本地文件系统实现，后续可替换为 MinIO / OSS。
 * </p>
 *
 * @author C1ouD
 */
public interface FileStorageService {

    /**
     * 存储上传文件，返回唯一文件标识（URL 或路径）。
     *
     * @param file 上传文件
     * @return 文件标识（如 {@code file:///tmp/atlas/upload-abc123.pdf}）
     * @throws IOException 写入失败
     */
    String store(MultipartFile file) throws IOException;

    /**
     * 将文件标识解析为可读的本地路径。
     *
     * @param fileUrl 由 {@link #store(MultipartFile)} 返回的标识
     * @return 本地文件路径
     */
    Path resolvePath(String fileUrl);

    /**
     * 删除已存储的文件。
     *
     * @param fileUrl 由 {@link #store(MultipartFile)} 返回的标识
     */
    void delete(String fileUrl);
}
