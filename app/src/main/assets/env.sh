CUSTOM_DIR="$1"
PACKAGE_NAME="$2"

echo "FILES DIR : $CUSTOM_DIR"

export ANDROID_PACKAGE_NAME=$PACKAGE_NAME

export PATH=$CUSTOM_DIR/usr/bin:$CUSTOM_DIR/bin:$PATH

export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:+$LD_LIBRARY_PATH:}$CUSTOM_DIR/usr/lib"

export SSL_CERT_FILE="$CUSTOM_DIR/etc/ssl/cert.pem"
# For ncurses
export TERMINFO="$CUSTOM_DIR/usr/share/terminfo"

export HOME="$CUSTOM_DIR"
export PYTHONHOME="$CUSTOM_DIR"
export PYTHONPATH="$CUSTOM_DIR/usr/lib/python3.12:$CUSTOM_DIR/usr/lib/python3.12/site-packages:$CUSTOM_DIR/usr/lib/python3.12/lib-dynload"