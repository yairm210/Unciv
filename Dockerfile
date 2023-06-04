ARG ARG_COMPILE_BASE_IMAGE=accetto/ubuntu-vnc-xfce-opengl-g3
FROM $ARG_COMPILE_BASE_IMAGE as build
USER root
RUN  apt update && \
        apt upgrade -y && \
        apt install --fix-broken -y wget curl default-jre default-jdk unzip
WORKDIR /src
# Get dependencies
RUN wget -q -O packr-all-4.0.0.jar https://github.com/libgdx/packr/releases/download/4.0.0/packr-all-4.0.0.jar && \
    wget -q -O jre-linux-64.tar.gz https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.11%2B9/OpenJDK11U-jre_x64_linux_hotspot_11.0.11_9.tar.gz

# https://nieldw.medium.com/caching-gradle-binaries-in-a-docker-build-when-using-the-gradle-wrapper-277c17e7dd22
# Get gradle distribution
COPY *.gradle gradle.* gradlew /src/
COPY gradle /src/gradle
WORKDIR /src
RUN chmod +x ./gradlew && ./gradlew --version

# Build unciv
COPY . /src/
RUN chmod +x ./gradlew && ./gradlew desktop:classes
RUN ./gradlew desktop:dist
RUN ./gradlew desktop:zipLinuxFilesForJar
RUN ./gradlew desktop:packrLinux64 --stacktrace --info --daemon --scan
RUN cd /src/deploy && unzip Unciv-Linux64.zip

FROM accetto/ubuntu-vnc-xfce-opengl-g3 as run
WORKDIR /home/headless/Desktop/
COPY --chown=1001:1001 --from=build /src/deploy/* /usr/
COPY --chown=1001:1001 --from=build /src/desktop/build/libs/Unciv.jar /usr/share/Unciv/Unciv.jar
COPY --chown=1001:1001 --chmod=0755 --from=build /src/desktop/linuxFilesForJar/* /home/headless/Desktop/
USER 1001
CMD [ "/home/headless/Desktop/Unciv.sh" ]
