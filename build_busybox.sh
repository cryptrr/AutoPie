echo "Starting building busybox for AutoPie"

git clone "https://github.com/meefik/busybox"

cd busybox/contrib || exit

./build.sh arm64

cp build/dist/arm64-v8a/busybox ../../app/src/main/assets

cp build/dist/arm64-v8a/ssl_helper ../../app/src/main/assets

echo "Successfully copied busybox binaries to assets"

echo "You are now ready to build AutoPie"