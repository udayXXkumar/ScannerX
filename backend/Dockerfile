FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY . .
RUN ./mvnw -q -DskipTests package

FROM kalilinux/kali-rolling

ENV DEBIAN_FRONTEND=noninteractive
ENV GOBIN=/usr/local/bin
ENV PATH="/usr/local/bin:${PATH}"

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    ffuf \
    git \
    golang-go \
    nikto \
    openjdk-21-jre-headless \
    python3 \
    python3-pip \
    sqlmap \
    unzip \
    whatweb \
    zaproxy \
    && rm -rf /var/lib/apt/lists/*

RUN go install github.com/projectdiscovery/nuclei/v3/cmd/nuclei@latest \
    && go install github.com/hahwul/dalfox/v2@latest

RUN mkdir -p /root/nuclei-templates \
    && git clone --depth=1 https://github.com/projectdiscovery/nuclei-templates.git /root/nuclei-templates

WORKDIR /app
COPY --from=builder /build/target/app-0.0.1-SNAPSHOT.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
