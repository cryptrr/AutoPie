CUSTOM_DIR="$1"

echo "FILES DIR : $CUSTOM_DIR"

export PATH=$PATH:$CUSTOM_DIR/build/usr/bin
if [ ! -z "$LD_LIBRARY_PATH" ] ; then
    export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:"
fi
export LD_LIBRARY_PATH="$LD_LIBRARY_PATH$CUSTOM_DIR/build/usr/lib"
export SSL_CERT_FILE="$CUSTOM_DIR/build/etc/ssl/cert.pem"
# For ncurses
export TERMINFO="$CUSTOM_DIR/build/usr/share/terminfo"

export HOME="$CUSTOM_DIR"
export PYTHONHOME="$CUSTOM_DIR/build"
export PYTHONPATH="$CUSTOM_DIR/build/usr/lib/python3.9:$CUSTOM_DIR/build/usr/lib/python3.9/site-packages:$CUSTOM_DIR/build/usr/lib/python3.9/lib-dynload"