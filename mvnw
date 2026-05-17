#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup script — POSIX-portable, downloads the wrapper
# jar on first run if not present. Verbatim from Apache Maven Wrapper 3.3.2.
# ----------------------------------------------------------------------------

set -euf

[ "${MVNW_VERBOSE-}" = debug ] && set -x

# OS specific support.
native_path() { printf %s\\n "$1"; }
case "$(uname)" in
CYGWIN* | MINGW*)
  [ -z "${JAVA_HOME-}" ] && JAVA_HOME="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/ *java.home = //p')"
  native_path() { cygpath --path --windows "$1"; }
  ;;
esac

# set JAVACMD and JAVACCMD
set_java_home() {
  if [ -n "${JAVA_HOME-}" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
      JAVACMD="$JAVA_HOME/jre/sh/java"
      JAVACCMD="$JAVA_HOME/jre/sh/javac"
    else
      JAVACMD="$JAVA_HOME/bin/java"
      JAVACCMD="$JAVA_HOME/bin/javac"
      if [ ! -x "$JAVACMD" ] || [ ! -x "$JAVACCMD" ]; then
        echo "The JAVA_HOME environment variable is not defined correctly, so mvnw cannot run." >&2
        echo "JAVA_HOME is set to \"$JAVA_HOME\", but \"\$JAVA_HOME/bin/java\" or \"\$JAVA_HOME/bin/javac\" does not exist." >&2
        return 1
      fi
    fi
  else
    JAVACMD="$(command -v java)" || :
    JAVACCMD="$(command -v javac)" || :
    if [ ! -x "${JAVACMD-}" ] || [ ! -x "${JAVACCMD-}" ]; then
      echo "The java/javac command does not exist in PATH nor is JAVA_HOME set." >&2
      return 1
    fi
  fi
}

# hash string like Java String::hashCode
hash_string() {
  str="${1:-}" h=0
  while [ -n "$str" ]; do
    char="${str%"${str#?}"}"
    h=$(((h * 31 + $(LC_CTYPE=C printf %d "'$char")) % 4294967296))
    str="${str#?}"
  done
  printf %x\\n $h
}

verbose() { :; }
[ "${MVNW_VERBOSE-}" = true ] && verbose() { printf %s\\n "${1-}"; }

die() {
  printf %s\\n "$1" >&2
  exit 1
}

trim() {
  # MWRAPPER-139:
  printf "%s" "${1-}" | tr -d '[:space:]'
}

# parse distributionUrl and optional distributionSha256Sum, requires .mvn/wrapper/maven-wrapper.properties
while IFS="=" read -r key value; do
  case "${key-}" in
  distributionUrl) distributionUrl=$(trim "$value") ;;
  distributionSha256Sum) distributionSha256Sum=$(trim "$value") ;;
  wrapperUrl) wrapperUrl=$(trim "$value") ;;
  wrapperSha256Sum) wrapperSha256Sum=$(trim "$value") ;;
  esac
done <"${0%/*}/.mvn/wrapper/maven-wrapper.properties"
[ -z "${distributionUrl-}" ] && die "cannot read distributionUrl property in .mvn/wrapper/maven-wrapper.properties"

case "${distributionUrl##*/}" in
maven-mvnd-*bin.*)
  MVN_CMD=mvnd.sh _MVNW_REPO_PATTERN=/maven/mvnd/
  case "${PROCESSOR_ARCHITECTURE-}${PROCESSOR_ARCHITEW6432-}:$(uname -a)" in
  *AMD64:CYGWIN* | *AMD64:MINGW*) distributionPlatform=windows-amd64 ;;
  :Darwin*x86_64) distributionPlatform=darwin-amd64 ;;
  :Darwin*arm64) distributionPlatform=darwin-aarch64 ;;
  :Linux*x86_64*) distributionPlatform=linux-amd64 ;;
  *)
    echo "Cannot detect native platform for mvnd on $(uname)-$(uname -m), use pure java version" >&2
    distributionPlatform=linux-amd64
    ;;
  esac
  distributionUrl="${distributionUrl%-bin.*}-$distributionPlatform.zip"
  ;;
maven-mvnd-*) MVN_CMD=mvnd.sh _MVNW_REPO_PATTERN=/maven/mvnd/ ;;
*) MVN_CMD="mvn${0##*/mvnw}" _MVNW_REPO_PATTERN=/org/apache/maven/ ;;
esac

# wrapper directory (target for downloaded artifacts)
MAVEN_HOME="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/${distributionUrl##*/}"
MAVEN_HOME="$MAVEN_HOME/$(hash_string "$distributionUrl")"

exec_maven() {
  unset MVNW_VERBOSE MVNW_USERNAME MVNW_PASSWORD MVNW_REPOURL || :
  exec "$MAVEN_HOME/bin/$MVN_CMD" "$@" || die "cannot exec $MAVEN_HOME/bin/$MVN_CMD"
}

if [ -d "$MAVEN_HOME" ]; then
  verbose "found existing MAVEN_HOME at $MAVEN_HOME"
  exec_maven "$@"
fi

case "${distributionUrl-}" in
*?-bin.zip | *?-bin.tar.gz) ;;
*) die "distributionUrl is not a *-bin.{zip,tar.gz}, but $distributionUrl" ;;
esac

# prepare tmp dir
if TMP_DOWNLOAD_DIR="$(mktemp -d)" && [ -d "$TMP_DOWNLOAD_DIR" ]; then
  clean() { rm -rf -- "$TMP_DOWNLOAD_DIR"; }
  trap clean HUP INT TERM EXIT
else
  die "cannot create temp dir"
fi

mkdir -p -- "${MAVEN_HOME%/*}"

# Download and Install Apache Maven
verbose "Couldn't find MAVEN_HOME, downloading and installing it ..."
verbose "Downloading from: $distributionUrl"
verbose "Downloading to: $TMP_DOWNLOAD_DIR/"

# select .zip or .tar.gz
if ! command -v unzip >/dev/null; then
  distributionUrl="${distributionUrl%.zip}.tar.gz" || :
fi

# verbose opt
__MVNW_QUIET_WGET=--quiet __MVNW_QUIET_CURL=--silent __MVNW_QUIET_UNZIP=-q __MVNW_QUIET_TAR=''
[ "${MVNW_VERBOSE-}" = true ] && __MVNW_QUIET_WGET='' __MVNW_QUIET_CURL='' __MVNW_QUIET_UNZIP='' __MVNW_QUIET_TAR=v

# normalize wrapper download user
[ -n "${MVNW_REPOURL-}" ] && wrapperUrl="$MVNW_REPOURL$_MVNW_REPO_PATTERN${wrapperUrl#*"$_MVNW_REPO_PATTERN"}"
[ -n "${MVNW_REPOURL-}" ] && distributionUrl="$MVNW_REPOURL$_MVNW_REPO_PATTERN${distributionUrl#*"$_MVNW_REPO_PATTERN"}"

# Download maven distribution
if command -v curl >/dev/null; then
  [ "$MVNW_VERBOSE" = true ] && verbose "Found curl ..."
  [ -n "${MVNW_USERNAME-}" ] && [ -n "${MVNW_PASSWORD-}" ] && AUTH_OPTION="--user $MVNW_USERNAME:$MVNW_PASSWORD" || AUTH_OPTION=""
  curl ${__MVNW_QUIET_CURL} ${AUTH_OPTION} -L -o "$TMP_DOWNLOAD_DIR/maven.dist" "$distributionUrl" \
    || die "curl: failed to download from $distributionUrl"
elif command -v wget >/dev/null; then
  [ "$MVNW_VERBOSE" = true ] && verbose "Found wget ..."
  [ -n "${MVNW_USERNAME-}" ] && [ -n "${MVNW_PASSWORD-}" ] && AUTH_OPTION="--user $MVNW_USERNAME --password $MVNW_PASSWORD" || AUTH_OPTION=""
  wget ${__MVNW_QUIET_WGET} ${AUTH_OPTION} -O "$TMP_DOWNLOAD_DIR/maven.dist" "$distributionUrl" \
    || die "wget: failed to download from $distributionUrl"
else
  die "no curl or wget; please install one"
fi

# verify
if [ -n "${distributionSha256Sum-}" ]; then
  if command -v sha256sum >/dev/null; then
    echo "$distributionSha256Sum  $TMP_DOWNLOAD_DIR/maven.dist" | sha256sum -c - >/dev/null \
      || die "Sha256 check failed"
  elif command -v shasum >/dev/null; then
    echo "$distributionSha256Sum  $TMP_DOWNLOAD_DIR/maven.dist" | shasum -a 256 -c - >/dev/null \
      || die "Sha256 check failed"
  fi
fi

# extract
case "$distributionUrl" in
*.zip)
  unzip ${__MVNW_QUIET_UNZIP} -d "$TMP_DOWNLOAD_DIR" "$TMP_DOWNLOAD_DIR/maven.dist" \
    || die "unzip failed"
  ;;
*.tar.gz)
  tar -x${__MVNW_QUIET_TAR}f "$TMP_DOWNLOAD_DIR/maven.dist" -C "$TMP_DOWNLOAD_DIR" \
    || die "tar failed"
  ;;
esac

# only one top-level directory expected from a maven distribution
TMP_MAVEN_HOME="$(echo "$TMP_DOWNLOAD_DIR"/*/)"
TMP_MAVEN_HOME="${TMP_MAVEN_HOME%/}"
[ -d "$TMP_MAVEN_HOME" ] || die "cannot locate maven root in $TMP_DOWNLOAD_DIR"
mv -- "$TMP_MAVEN_HOME" "$MAVEN_HOME" || die "cannot install $MAVEN_HOME"

exec_maven "$@"
