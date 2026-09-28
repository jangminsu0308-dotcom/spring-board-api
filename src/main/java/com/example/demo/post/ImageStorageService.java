package com.example.demo.post;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/** 게시글 첨부 이미지를 디스크(uploadDir)에 저장/삭제한다. DB에는 파일명만 남고,
 *  실제 바이트는 여기서만 다룬다 — PostService는 파일 시스템을 몰라도 된다. */
@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final Path uploadDir;

    public ImageStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir);
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidImageException("업로드할 이미지를 선택해주세요");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidImageException("이미지 크기는 5MB를 넘을 수 없습니다");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidImageException("jpg, png, gif, webp 이미지만 업로드할 수 있습니다");
        }

        String extension = extensionFor(contentType);
        String filename = UUID.randomUUID() + extension;
        try {
            file.transferTo(uploadDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return filename;
    }

    public void delete(String filename) {
        if (filename == null) return;
        try {
            Files.deleteIfExists(uploadDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}
