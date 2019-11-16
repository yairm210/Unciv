rem See https://github.com/libgdx/packr

java -jar packr.jar \
     --platform windows64 \
     --jdk openjdk-1.7.0-u45-unofficial-icedtea-2.4.3-macosx-x86_64-image.zip \
     --executable myapp \
     --classpath myapp.jar \
     --removelibs myapp.jar \
     --mainclass com.my.app.MainClass \
     --vmargs Xmx1G \
     --resources src/main/resources path/to/other/assets \
     --minimizejre soft \
     --output out-mac