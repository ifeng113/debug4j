#!/bin/sh
set -e

echo "Installing openssh-server and enabling root login..."

# ---------- 1. 检测是否 root ----------
if [ "$(id -u)" -eq 0 ]; then
    SUDO=""
else
    SUDO="sudo"
    echo "Not running as root, using sudo for package installation and service commands."
fi

# ---------- 2. 安装 openssh-server ----------
if command -v apt >/dev/null 2>&1; then
    $SUDO apt update
    $SUDO apt install -y openssh-server sshpass

elif command -v yum >/dev/null 2>&1; then
    $SUDO yum install -y openssh-server sshpass

elif command -v dnf >/dev/null 2>&1; then
    $SUDO dnf install -y openssh-server sshpass

elif command -v pacman >/dev/null 2>&1; then
    $SUDO pacman -S --noconfirm openssh sshpass

elif command -v apk >/dev/null 2>&1; then
    $SUDO apk add --no-cache openssh sshpass

else
    echo "No supported package manager found"
    exit 1
fi

# ---------- 3. 配置 root 登录 ----------
ROOT_PASS="debug4j123"  # 可修改
echo "Setting root password..."
echo "root:$ROOT_PASS" | $SUDO chpasswd

SSHD_CONFIG="/etc/ssh/sshd_config"
$SUDO cp $SSHD_CONFIG ${SSHD_CONFIG}.bak

# PermitRootLogin yes
if grep -q "^PermitRootLogin" $SSHD_CONFIG; then
    $SUDO sed -i 's/^PermitRootLogin.*/PermitRootLogin yes/' $SSHD_CONFIG
else
    echo "PermitRootLogin yes" | $SUDO tee -a $SSHD_CONFIG >/dev/null
fi

# PasswordAuthentication yes
if grep -q "^PasswordAuthentication" $SSHD_CONFIG; then
    $SUDO sed -i 's/^PasswordAuthentication.*/PasswordAuthentication yes/' $SSHD_CONFIG
else
    echo "PasswordAuthentication yes" | $SUDO tee -a $SSHD_CONFIG >/dev/null
fi

# ---------- 4. 启动 ssh 服务 ----------
if command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]; then
    echo "Starting sshd via systemd..."
    $SUDO systemctl enable sshd --now 2>/dev/null || $SUDO systemctl enable ssh --now 2>/dev/null || true
elif command -v service >/dev/null 2>&1; then
    echo "Starting sshd via service command..."
    $SUDO service ssh start || $SUDO service sshd start || echo "Warning: failed to start ssh via service"
elif command -v rc-service >/dev/null 2>&1; then
    echo "Starting sshd via rc-service..."
    $SUDO rc-service sshd restart || echo "Warning: failed to start ssh via rc-service"
else
    echo "No known init system found, please start ssh manually"
fi

# ---------- 5. 测试 root SSH 本地连接 ----------
echo "Testing root SSH login..."
if command -v sshpass >/dev/null 2>&1; then
    sshpass -p "$ROOT_PASS" ssh -o StrictHostKeyChecking=no root@localhost "echo SSH_ROOT_OK"
else
    echo "sshpass not installed, skipping automatic root login test"
    echo "You can manually test: ssh root@localhost"
fi

echo "openssh-server installation complete. Root login enabled."