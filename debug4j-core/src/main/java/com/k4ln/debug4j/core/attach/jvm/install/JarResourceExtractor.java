package com.k4ln.debug4j.core.attach.jvm.install;

import com.k4ln.debug4j.common.utils.SystemUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

@Slf4j
public class JarResourceExtractor {

    public static final Path targetDir = Paths.get("/usr/local/bin/debug4j");

    /**
     * 解压安装脚本
     */
    public static void extractInstall() {
        try {
            Files.createDirectories(targetDir);
            URL url = JarResourceExtractor.class.getResource("/install");
            if (url == null) {
                throw new RuntimeException("classpath:/install not found");
            }
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                Path installDir = Paths.get(url.toURI());
                copyDir(installDir);
                return;
            }
            if ("jar".equals(protocol)) {
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                try (JarFile jar = conn.getJarFile()) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        if (!e.getName().startsWith("install/")) continue;
                        String name = e.getName().substring("install/".length());
                        if (name.isEmpty()) continue;
                        Path out = targetDir.resolve(name);
                        if (e.isDirectory()) {
                            Files.createDirectories(out);
                        } else {
                            Files.createDirectories(out.getParent());
                            if (!Files.exists(out)) {
                                try (InputStream in = jar.getInputStream(e)) {
                                    log.info("jar mode start copying file: {}", out.getFileName());
                                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                                }
                            }
                        }
                    }
                }
                makeExecutable();
                return;
            }
            throw new RuntimeException("Unsupported protocol: " + protocol);
        } catch (Exception e) {
            throw new RuntimeException("Extract install failed", e);
        }
    }

    /**
     * 拷贝脚本
     *
     * @param src
     */
    private static void copyDir(Path src) {
        try (Stream<Path> paths = Files.walk(src)) {
            paths.forEach(in -> {
                try {
                    Path out = JarResourceExtractor.targetDir.resolve(src.relativize(in).toString());
                    if (Files.isDirectory(in)) {
                        Files.createDirectories(out);
                    } else {
                        if (!Files.exists(out)) {
                            log.info("file mode start copying file: {}", out.getFileName());
                            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            makeExecutable();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置可执行权限
     */
    private static void makeExecutable() {
        try (Stream<Path> paths = Files.walk(JarResourceExtractor.targetDir)) {
            paths.filter(p -> p.toString().endsWith(".sh"))
                    .forEach(p -> {
                        try {
                            p.toFile().setExecutable(true, false);
                        } catch (Exception e) {
                            log.warn("Error setting executable permission for: {}, cause: {}", p, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 执行安装脚本
     */
    public static Process runSSHInstall() {
        return SystemUtils.exec("sh", targetDir.resolve("install-ssh.sh").toString());
    }
}
