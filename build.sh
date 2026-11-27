#!/bin/bash
# Builds Mane3.apk: javac -> dx -> apktool -> sign
set -e
cd "$(dirname "$0")"
mkdir -p tools out
[ -f tools/apktool.jar ] || curl -sL -o tools/apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar
[ -f tools/uber-signer.jar ] || curl -sL -o tools/uber-signer.jar https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar
[ -f tools/android.jar ] || curl -sL -o tools/android.jar https://raw.githubusercontent.com/Sable/android-platforms/master/android-28/android.jar
if [ ! -f tools/dx-30.0.2.jar ]; then
  curl -sL -o /tmp/dex-tools.zip https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip
  unzip -qo -j /tmp/dex-tools.zip 'dex-tools-v2.4/lib/dx-30.0.2.jar' -d tools
fi
rm -rf out/* build && mkdir -p build/classes
javac --release 8 -nowarn -cp tools/android.jar -d build/classes $(find src -name '*.java')
java -cp tools/dx-30.0.2.jar com.android.dx.command.Main --dex --min-sdk-version=24 --output=build/classes.dex build/classes
java -jar tools/apktool.jar b app -o out/unsigned.apk
(cd build && zip -q ../out/unsigned.apk classes.dex)
java -jar tools/uber-signer.jar -a out/unsigned.apk -o out/signed
cp out/signed/unsigned-aligned-debugSigned.apk out/Mane3.apk
echo "Done: out/Mane3.apk"
