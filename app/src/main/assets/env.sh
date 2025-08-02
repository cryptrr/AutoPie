CUSTOM_DIR="$1"
PACKAGE_NAME="$2"

echo "FILES DIR : $CUSTOM_DIR"

export ANDROID_PACKAGE_NAME=$PACKAGE_NAME

export PATH=$CUSTOM_DIR/build/usr/bin:$CUSTOM_DIR/build/bin:$PATH

if [ ! -z "$LD_LIBRARY_PATH" ] ; then
    export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:"
fi
export LD_LIBRARY_PATH="$LD_LIBRARY_PATH$CUSTOM_DIR/build/usr/lib"
export SSL_CERT_FILE="$CUSTOM_DIR/build/etc/ssl/cert.pem"
# For ncurses
export TERMINFO="$CUSTOM_DIR/build/usr/share/terminfo"

export HOME="$CUSTOM_DIR"
export PYTHONHOME="$CUSTOM_DIR/build"
export PYTHONPATH="$CUSTOM_DIR/build/usr/lib/python3.12:$CUSTOM_DIR/build/usr/lib/python3.12/site-packages:$CUSTOM_DIR/build/usr/lib/python3.12/lib-dynload"