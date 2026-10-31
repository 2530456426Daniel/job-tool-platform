#!/bin/sh
#
# 项目专用 Maven 启动脚本（仅作用于本目录，不修改任何全局配置）
#
#   ./mvnw clean test
#   ./mvnw spring-boot:run
#
# 行为：
#   1. 固定使用本机安装的 Maven 3.10.0（不使用 PATH 里的旧版 maven）；
#   2. 固定本地仓库与项目 settings（见 .mvn/settings.xml），
#      保证依赖只从开源公共仓库下载，不读取可能被改坏的全局配置；
#   3. 不修改 PATH、不修改 ~/.zshrc、不影响其它项目。
#
# 如需更换 Maven 版本或仓库路径，改下面的变量即可。
#
set -e

# 本项目使用的 Maven 安装目录
MAVEN_HOME="${MAVEN_HOME_OVERRIDE:-/Users/pengfei/Documents/work/coding/maven/apache-maven-3.10.0}"

# 本机本地仓库
MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL_OVERRIDE:-/Users/pengfei/Documents/work/coding/maven/repository}"

# 项目目录（脚本所在目录）
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

MVN_BIN="$MAVEN_HOME/bin/mvn"

if [ ! -x "$MVN_BIN" ]; then
  echo "[mvnw] 找不到可执行的 Maven：$MVN_BIN" >&2
  echo "[mvnw] 请修改本脚本顶部的 MAVEN_HOME，或设置环境变量 MAVEN_HOME_OVERRIDE" >&2
  exit 1
fi

if [ ! -d "$MAVEN_REPO_LOCAL" ]; then
  echo "[mvnw] 本地仓库目录不存在：$MAVEN_REPO_LOCAL" >&2
  echo "[mvnw] 请修改本脚本顶部的 MAVEN_REPO_LOCAL，或设置环境变量 MAVEN_REPO_LOCAL_OVERRIDE" >&2
  exit 1
fi

exec "$MVN_BIN" \
  -s "$BASE_DIR/.mvn/settings.xml" \
  -Dmaven.repo.local="$MAVEN_REPO_LOCAL" \
  "$@"
