package com.example.badukanalyzer.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class TygemFileWatcherService {

    @Value("${tygem.gibo-dir}")
    private String giboDir;

    @Value("${katago.record-dir}")
    private String recordDir;

    private final AnalysisService analysisService;
    private WatchService watchService;
    private final Map<WatchKey, Path> keyToDir = new HashMap<>();
    private Thread watchThread;
    private volatile boolean running = false;

    public TygemFileWatcherService(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostConstruct
    public void start() {
        Path giboDirPath = Paths.get(giboDir);
        if (!Files.exists(giboDirPath)) {
            log.warn("Tygem Gibo directory not found: {}", giboDir);
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerAll(giboDirPath);
            running = true;
            watchThread = new Thread(this::watch, "tygem-watcher");
            watchThread.setDaemon(true);
            watchThread.start();
            log.info("Tygem file watcher started: {}", giboDir);
        } catch (IOException e) {
            log.error("Failed to start Tygem file watcher: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
        try { if (watchService != null) watchService.close(); } catch (IOException ignored) {}
    }

    private void registerAll(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY);
        keyToDir.put(key, dir);
        log.debug("Watching directory: {}", dir);
    }

    private void watch() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            Path dir = keyToDir.get(key);
            if (dir == null) { key.reset(); continue; }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                Path filename = ((WatchEvent<Path>) event).context();
                Path fullPath = dir.resolve(filename);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    if (Files.isDirectory(fullPath)) {
                        // 새 월별 폴더(예: 2026-07) 생성 시 감시 등록
                        try { register(fullPath); } catch (IOException e) {
                            log.warn("Failed to register new directory: {}", fullPath);
                        }
                    } else if (fullPath.toString().toLowerCase().endsWith(".gib")) {
                        copyToRecordDir(fullPath);
                    }
                }
            }
            key.reset();
        }
        log.info("Tygem file watcher stopped.");
    }

    private void copyToRecordDir(Path source) {
        try {
            Thread.sleep(500);

            Path targetDir = Paths.get(recordDir);
            Files.createDirectories(targetDir);

            Path target = targetDir.resolve(source.getFileName());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("New Tygem game copied: {} -> {}", source.getFileName(), target);

            // 새 기보 감지 시 분석 재실행
            analysisService.startBackgroundAnalysis();
        } catch (IOException e) {
            log.error("Failed to copy Tygem game file: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}