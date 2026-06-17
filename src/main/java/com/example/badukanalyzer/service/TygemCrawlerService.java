package com.example.badukanalyzer.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TygemCrawlerService {

    @Value("${katago.record-dir}")
    private String recordDir;

    private volatile boolean running = false;
    private volatile String statusMessage = "대기 중";
    private final AtomicInteger downloadCount = new AtomicInteger(0);
    private volatile String errorMessage = null;

    public boolean isRunning() { return running; }
    public String getStatusMessage() { return statusMessage; }
    public int getDownloadCount() { return downloadCount.get(); }
    public String getErrorMessage() { return errorMessage; }

    public boolean startFetch(String username, String password) {
        if (running) return false;
        errorMessage = null;
        downloadCount.set(0);
        statusMessage = "시작 중...";
        new Thread(() -> fetchAll(username, password)).start();
        return true;
    }

    private void fetchAll(String username, String password) {
        running = true;

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        try {
            login(driver, username, password);
            List<String> gameIds = collectGameIds(driver);
            if (gameIds.isEmpty()) {
                statusMessage = "가져온 기보가 없습니다. 로그인 또는 페이지 구조를 확인해주세요.";
            } else {
                downloadGames(driver, gameIds);
                statusMessage = "완료! " + downloadCount.get() + "개 기보 다운로드";
            }
        } catch (Exception e) {
            log.error("Tygem fetch failed: {}", e.getMessage(), e);
            errorMessage = e.getMessage();
            statusMessage = "오류 발생";
        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
            running = false;
        }
    }

    private void login(WebDriver driver, String username, String password) throws InterruptedException {
        statusMessage = "타이젬 로그인 중...";
        driver.get("https://www.tygem.com/login.php");
        Thread.sleep(2500);

        // 디버그: 페이지 소스 저장
        saveDebugHtml(driver, "login_page.html");

        WebElement usernameField = findBySelectors(driver,
            "input[name='m_id']", "input[name='userid']", "input[name='id']", "input[type='text']"
        );
        WebElement passwordField = findBySelectors(driver, "input[type='password']");

        usernameField.clear();
        usernameField.sendKeys(username);
        passwordField.clear();
        passwordField.sendKeys(password);

        // 버튼 클릭: 셀렉터 실패 시 엔터키로 대체
        boolean clicked = false;
        String[] btnSelectors = {
            "input[type='submit']", "button[type='submit']", "button[type='button']",
            "input[type='image']", ".login_btn", ".btn_login", ".loginBtn", ".btn-login",
            "a.btn_login", "a[onclick*='login']", "a[href*='login']"
        };
        for (String sel : btnSelectors) {
            List<WebElement> els = driver.findElements(By.cssSelector(sel));
            if (!els.isEmpty()) {
                log.info("Login button found with selector: {}", sel);
                try {
                    ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", els.get(0));
                } catch (Exception e) {
                    els.get(0).click();
                }
                clicked = true;
                break;
            }
        }
        if (!clicked) {
            // 버튼을 못 찾으면 엔터키로 제출
            log.warn("Login button not found, trying Enter key");
            passwordField.sendKeys(org.openqa.selenium.Keys.RETURN);
        }

        Thread.sleep(3000);
        log.info("Login done. Current URL: {}", driver.getCurrentUrl());
        saveDebugHtml(driver, "after_login.html");
    }

    private void saveDebugHtml(WebDriver driver, String filename) {
        try {
            Path debugFile = Paths.get(System.getProperty("user.home"), filename);
            Files.writeString(debugFile, driver.getPageSource());
            log.info("Debug HTML saved: {}", debugFile);
        } catch (Exception e) {
            log.warn("Could not save debug HTML: {}", e.getMessage());
        }
    }

    private List<String> collectGameIds(WebDriver driver) throws InterruptedException {
        statusMessage = "기보 목록 수집 중...";
        List<String> gameIds = new ArrayList<>();

        navigateToMyGames(driver);

        Pattern idPattern = Pattern.compile("giboOpenWindow\\((\\d+)");
        int page = 1;

        while (true) {
            Thread.sleep(1500);
            String pageSource = driver.getPageSource();
            Matcher matcher = idPattern.matcher(pageSource);

            Set<String> pageIds = new LinkedHashSet<>();
            while (matcher.find()) {
                pageIds.add(matcher.group(1));
            }

            if (pageIds.isEmpty()) {
                log.info("No game IDs found on page {}", page);
                break;
            }

            gameIds.addAll(pageIds);
            statusMessage = "기보 목록 수집 중... " + gameIds.size() + "개";
            log.info("Page {}: {} games found, total {}", page, pageIds.size(), gameIds.size());

            // 다음 페이지 이동
            boolean moved = false;
            List<WebElement> candidates = driver.findElements(By.cssSelector("a"));
            for (WebElement a : candidates) {
                String text = a.getText().trim();
                String href = a.getAttribute("href");
                if ((text.equals(">") || text.equals("다음") || text.equals("▶"))
                        || (href != null && href.contains("page=" + (page + 1)))) {
                    a.click();
                    page++;
                    moved = true;
                    break;
                }
            }
            if (!moved) break;
        }

        log.info("Total game IDs collected: {}", gameIds.size());
        return gameIds;
    }

    private void navigateToMyGames(WebDriver driver) throws InterruptedException {
        // service.tygem.com 로그인 시도 (별도 서브도메인이므로 쿠키 공유 안 될 수 있음)
        driver.get("https://service.tygem.com/webgame/webgame.php");
        Thread.sleep(2000);
        saveDebugHtml(driver, "service_main.html");

        // 가능한 내 기보 URL들 순서대로 시도
        String[] urls = {
            "https://service.tygem.com/webgame/mygibo.php",
            "https://service.tygem.com/myzone/mygibo.php",
            "https://service.tygem.com/baduk/mygibo.php",
            "https://www.tygem.com/myzone/mygibo.php",
            "https://www.tygem.com/myzone/mygame.php",
            "https://www.tygem.com/myzone/gamelog.php",
            "https://www.tygem.com/myzone/game_history.php"
        };

        for (String url : urls) {
            driver.get(url);
            Thread.sleep(1500);
            String current = driver.getCurrentUrl();
            String src = driver.getPageSource();
            log.info("Tried URL: {} -> current: {} (has giboOpenWindow: {})", url, current, src.contains("giboOpenWindow"));
            if (!current.contains("login") && !current.contains("error") && !current.contains("main.php")) {
                saveDebugHtml(driver, "mygibo_page.html");
                log.info("My games page found: {}", current);
                return;
            }
        }

        // 마지막으로 www.tygem.com 메인에서 "내 기보" 텍스트 링크 탐색
        driver.get("https://www.tygem.com/myzone/main.php");
        Thread.sleep(2000);
        for (WebElement link : driver.findElements(By.tagName("a"))) {
            String text = link.getText().trim();
            String href = link.getAttribute("href");
            if (href == null || href.contains("tygemgame.com")) continue;
            if (text.contains("기보") || (href.contains("gibo") && !href.contains("news"))) {
                log.info("Found gibo link: text='{}' href='{}'", text, href);
                link.click();
                Thread.sleep(2000);
                saveDebugHtml(driver, "mygibo_page.html");
                return;
            }
        }
        saveDebugHtml(driver, "mygibo_page.html");
        log.warn("Could not find my games page. Current: {}", driver.getCurrentUrl());
    }

    private void downloadGames(WebDriver driver, List<String> gameIds) throws Exception {
        Path targetDir = Paths.get(recordDir);
        Files.createDirectories(targetDir);

        String cookieHeader = buildCookieHeader(driver.manage().getCookies());

        HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

        String[] urlPatterns = {
            "https://www.tygem.com/gibo/download.php?game_id=%s",
            "https://www.tygem.com/gibo/download.php?seq=%s",
            "https://www.tygem.com/gibo/%s.gib"
        };

        for (int i = 0; i < gameIds.size(); i++) {
            String gameId = gameIds.get(i);
            statusMessage = "다운로드 중... " + (i + 1) + "/" + gameIds.size();

            Path targetFile = targetDir.resolve(gameId + ".gib");
            if (Files.exists(targetFile)) {
                downloadCount.incrementAndGet();
                continue;
            }

            for (String pattern : urlPatterns) {
                if (tryDownload(httpClient, String.format(pattern, gameId), cookieHeader, targetFile)) {
                    downloadCount.incrementAndGet();
                    break;
                }
            }

            Thread.sleep(200);
        }
    }

    private boolean tryDownload(HttpClient client, String url, String cookieHeader, Path targetFile) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Cookie", cookieHeader)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                .GET()
                .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) return false;

            byte[] body = response.body();
            if (body.length < 10) return false;

            String start = new String(body, 0, Math.min(200, body.length), Charset.forName("EUC-KR"));
            if (!start.contains("\\[") && !start.startsWith("(")) return false;

            Files.write(targetFile, body);
            log.info("Downloaded: {}", targetFile.getFileName());
            return true;
        } catch (Exception e) {
            log.debug("Download failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    private String buildCookieHeader(Set<Cookie> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Cookie c : cookies) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(c.getName()).append("=").append(c.getValue());
        }
        return sb.toString();
    }

    private WebElement findBySelectors(WebDriver driver, String... selectors) {
        for (String selector : selectors) {
            List<WebElement> els = driver.findElements(By.cssSelector(selector));
            if (!els.isEmpty()) return els.get(0);
        }
        throw new org.openqa.selenium.NoSuchElementException("Element not found. Tried: " + Arrays.toString(selectors));
    }
}