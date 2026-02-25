FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
      --no-install-recommends \
      bash \
      build-essential \
      ca-certificates \
      clang \
      curl \
      file \
      git \
      libcurl4-openssl-dev \
      libssl-dev \
      pkg-config \
      unzip \
      xz-utils \
      zlib1g-dev \
      zip && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

ENV GRADLE_USER_HOME=/gradle-home
