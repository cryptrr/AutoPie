echo "Removing the existing built binaries"

rm app/src/main/assets/build-aarch64-api27.tar.xz
rm app/src/main/assets/busybox
rm app/src/main/assets/ssl_helper

echo "Starting building binaries"

./build_python.sh

./build_busybox.sh