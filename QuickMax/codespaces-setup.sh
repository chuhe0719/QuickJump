#!/usr/bin/env bash
# GitHub Codespaces 一键环境搭建 + 打包脚本
# 用法：在仓库根目录终端执行  bash codespaces-setup.sh
set -e
cd "$(dirname "$0")"

echo "==> [1/5] 安装 JDK 17 与 unzip ..."
sudo apt-get update -qq
sudo apt-get install -y -qq unzip openjdk-17-jdk

echo "==> [2/5] 安装 Android 命令行工具 ..."
if [ ! -d "$HOME/android-sdk/cmdline-tools/latest" ]; then
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/tools.zip
  mkdir -p "$HOME/android-sdk/cmdline-tools"
  unzip -q /tmp/tools.zip -d /tmp/android-ctl
  mv /tmp/android-ctl/cmdline-tools "$HOME/android-sdk/cmdline-tools/latest"
fi

# 环境变量写入 ~/.bashrc，重启终端也生效
grep -q ANDROID_HOME ~/.bashrc || cat >> ~/.bashrc <<'EOF'
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
EOF
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

echo "==> [3/5] 安装 Android SDK 组件（platform 34 / build-tools 34） ..."
yes | sdkmanager --licenses > /dev/null || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" > /dev/null

echo "==> [4/5] 安装 Gradle 8.7 并生成 wrapper ..."
if ! command -v gradle > /dev/null; then
  wget -q https://services.gradle.org/distributions/gradle-8.7-bin.zip -O /tmp/gradle.zip
  sudo unzip -q /tmp/gradle.zip -d /opt
  sudo ln -sf /opt/gradle-8.7/bin/gradle /usr/local/bin/gradle
fi
gradle wrapper --gradle-version 8.7

echo "==> [5/5] 打包 Debug APK ..."
./gradlew assembleDebug

echo ""
echo "✅ 完成！APK 位置: app/build/outputs/apk/debug/app-debug.apk"
echo "   在左侧文件资源管理器中右键该文件 → Download 即可下载"
