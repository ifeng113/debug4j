#!/bin/sh
set -e

echo "===== Installing Arthas ====="

# ---------- 0. 参数解析 ----------
TARGET_JAVA="$1"
if [ -z "$TARGET_JAVA" ]; then
    echo "Usage: $0 <TARGET_JAVA_PID>"
    exit 1
fi
echo "Target Java PID: $TARGET_JAVA"

# ---------- 1. 检测是否 root ----------
if [ "$(id -u)" -eq 0 ]; then
    SUDO=""
else
    SUDO="sudo"
    echo "Not running as root, using sudo for commands that require privileges."
fi

# ---------- 2. 检测 Java ----------
if ! command -v java >/dev/null 2>&1; then
    echo "❌ Java not found, please install Java first."
    exit 1
fi

# ---------- 3. 检测 curl ----------
if ! command -v curl >/dev/null 2>&1; then
    echo "❌ curl not found, please install curl first."
    exit 1
fi

# ---------- 4. 定义安装目录 ----------
INSTALL_DIR="/opt/arthas"
mkdir -p "$INSTALL_DIR"

ARTHAS_URL="https://arthas.aliyun.com/arthas-boot.jar"
ARTHAS_JAR="$INSTALL_DIR/arthas-boot.jar"

# ---------- 5. 下载 Arthas ----------
if [ -f "$ARTHAS_JAR" ]; then
    echo "arthas-boot.jar already exists at $ARTHAS_JAR, skipping download"
else
    echo "Downloading Arthas to $INSTALL_DIR..."
    curl -fL --retry 3 --connect-timeout 10 -o "$ARTHAS_JAR" "$ARTHAS_URL"
fi

# ---------- 6. 启动 Arthas attach ----------
LOG_FILE="/tmp/arthas.log"
TELNET_PORT=3658
HTTP_PORT=8563

echo "Attaching Arthas to Java process PID $TARGET_JAVA in background..."
nohup java \
    -Darthas.telnetPort=$TELNET_PORT \
    -Darthas.httpPort=$HTTP_PORT \
    -jar "$ARTHAS_JAR" "$TARGET_JAVA" \
    >>"$LOG_FILE" 2>&1 &
ARTHAS_PID=$!

echo "Arthas attach command sent."
echo "Arthas PID: $ARTHAS_PID"
echo "Telnet port: $TELNET_PORT, HTTP port: $HTTP_PORT"
echo "Logs: $LOG_FILE"

# ---------- 7. 安装完成 ----------
echo "===== Arthas installation complete ====="
echo "To check Arthas logs: tail -f $LOG_FILE"
echo "To attach manually: java -jar $ARTHAS_JAR <pid>"
