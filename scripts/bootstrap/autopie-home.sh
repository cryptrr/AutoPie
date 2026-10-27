# Repair legacy launch environments before Bash reads the user's startup files.
# Preserve explicitly customized homes and derive the Android user/package from PREFIX.
case "${HOME-}" in
    /data/data/com.termux/files/home|/data/user/0/com.termux/files/home|"")
        case "${PREFIX-}" in
            /*/usr) export HOME="${PREFIX%/usr}/home" ;;
        esac
        ;;
esac

# A running shell can still be in Android's alternate spelling of this home.
# Only adjust the logical path when it is the very same directory; leave
# explicitly chosen working directories alone.
if [ -n "${HOME-}" ] && [ "${PWD-}" != "$HOME" ] && [ . -ef "$HOME" ]; then
    cd "$HOME"
fi
