package com.homepoker.stats;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/**
 * 통계 스냅샷을 JSON 파일 한 개로 영속화한다. DB 없이(=Lightsail 저사양 배려) 재시작 후에도
 * 리더보드가 살아남게 하는 가벼운 방식. 보존 대상이 플레이어별 소수의 카운터뿐이라 파일로 충분하다.
 *
 * 쓰기는 temp 파일에 쓴 뒤 원자적 move 로 교체해, 쓰다 죽어도 기존 파일이 깨지지 않게 한다.
 */
@Component
public class JsonFileStatsStore implements StatsStore {

    private final Path file;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public JsonFileStatsStore(@Value("${poker.stats.file:data/stats.json}") String path) {
        this.file = Path.of(path);
    }

    @Override
    public synchronized List<PlayerStatsSnapshot> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return Arrays.asList(mapper.readValue(Files.readAllBytes(file), PlayerStatsSnapshot[].class));
        } catch (IOException | RuntimeException e) {
            // 손상 파일이나 읽기 실패가 서버 기동을 막지 않게 한다(Jackson 3 파싱 예외는 unchecked).
            return List.of();
        }
    }

    @Override
    public synchronized void save(List<PlayerStatsSnapshot> snapshots) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, "stats", ".tmp");
            Files.write(tmp, mapper.writeValueAsBytes(snapshots));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("통계 저장 실패: " + file, e);
        }
    }
}
