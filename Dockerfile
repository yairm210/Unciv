ARG ARG_COMPILE_BASE_IMAGE=accetto/ubuntu-vnc-xfce-opengl-g3

FROM $ARG_COMPILE_BASE_IMAGE AS build

USER root
RUN apt update && \
    apt upgrade -y && \
    apt install --fix-broken -y wget curl openjdk-17-jdk unzip

# Gradle is dumb (https://github.com/gradle/gradle/issues/22921) and doesn't recognize the JDK location
# Solution from https://www.linux.org.ru/forum/desktop/17285826 ¯\_(ツ)_/¯
RUN rm -rf /usr/lib/jvm/openjdk-17 && \
    ln -s /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/openjdk-17

WORKDIR /src

# https://nieldw.medium.com/caching-gradle-binaries-in-a-docker-build-when-using-the-gradle-wrapper-277c17e7dd22
# Get gradle distribution first, so this layer caches independently of source changes
COPY *.gradle gradle.* gradlew /src/
COPY gradle /src/gradle
RUN chmod +x ./gradlew && ./gradlew --version

# Build unciv
COPY . /src/
RUN --mount=type=cache,target=/src/desktop/.jre-cache \
    chmod +x ./gradlew && \
    ./gradlew desktop:dist desktop:packrLinux64 --stacktrace --info

FROM accetto/ubuntu-vnc-xfce-opengl-g3 AS run
WORKDIR /home/headless/Desktop/
COPY --chown=1001:1001 --from=build /src/desktop/build/packr/linux64/* /usr/
COPY --chown=1001:1001 --from=build /src/desktop/build/libs/Unciv.jar /usr/share/Unciv/Unciv.jar
COPY --chown=1001:1001 --chmod=0755 --from=build /src/desktop/linuxFilesForJar/* /home/headless/Desktop/
USER 1001
CMD [ "/home/headless/Desktop/Unciv.sh" ]
