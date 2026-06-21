# Use the exact base image from your original devcontainer setup
FROM mcr.microsoft.com/devcontainers/javascript-node:20

# Switch to root to perform package installations
USER root

# Install OpenJDK 21, Maven, and clean up apt caches to keep the image slim
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-21-jdk-headless \
    maven \
    && rm -rf /var/lib/apt/lists/*

# Set the JAVA_HOME environment variable globally for all users
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"

# Switch back to the default non-root user provided by the base image
USER node