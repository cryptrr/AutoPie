CUSTOM_DIR="$1"
PACKAGE_NAME="$2"

echo "FILES DIR : $CUSTOM_DIR"

export ANDROID_PACKAGE_NAME=$PACKAGE_NAME

export PATH=$CUSTOM_DIR/usr/bin:$CUSTOM_DIR/bin:$PATH

if [ ! -z "$LD_LIBRARY_PATH" ] ; then
    export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:"
fi
export LD_LIBRARY_PATH="$LD_LIBRARY_PATH$CUSTOM_DIR/usr/lib"
export SSL_CERT_FILE="$CUSTOM_DIR/usr/etc/ssl/cert.pem"
# For ncurses
export TERMINFO="$CUSTOM_DIR/usr/share/terminfo"

export HOME="$CUSTOM_DIR"
