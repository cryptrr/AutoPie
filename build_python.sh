echo "Starting building Python for AutoPie"

git clone "https://github.com/cryptrr/python3-android"

cd python3-android

docker run --rm -it -v $(pwd):/python3-android -v $ANDROID_NDK_ROOT:/android-ndk:ro --env ARCH=arm64 --env ANDROID_API=27 python:3.9.0-slim /python3-android/docker-build.sh

#sudo chown -R $(id -u):$(id -g) build

mkdir copied

cp -r build copied/build

mkdir copied/build/etc

mkdir copied/build/etc/ssl

curl -o copied/build/etc/ssl/cert.pem "https://curl.se/ca/cacert.pem"

cd copied

tar -cJf ../build-aarch64-api27.tar.xz build

cd ../

cp build-aarch64-api27.tar.xz ../app/src/main/assets

echo "Successfully copied python archive to assets"