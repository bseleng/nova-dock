#!/usr/bin/env python3
"""Offline Android SDK build; no Gradle downloads or external libraries."""
import os
import pathlib
import shutil
import subprocess
import sys
import zipfile

ROOT=pathlib.Path(__file__).resolve().parent
sdk_default=pathlib.Path.home()/("Library/Android/sdk" if sys.platform=="darwin" else "Android/Sdk")
SDK=pathlib.Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or sdk_default)
java_home=os.environ.get("NOVA_JAVA_HOME") or os.environ.get("JAVA_HOME")
if not java_home and sys.platform=="darwin":
    java_home=subprocess.check_output(["/usr/libexec/java_home","-v","17"],text=True).strip()
if not java_home:
    javac=shutil.which("javac")
    if not javac: raise SystemExit("JDK 17 not found. Set NOVA_JAVA_HOME or JAVA_HOME.")
    java_home=str(pathlib.Path(javac).resolve().parent.parent)
JAVA=pathlib.Path(java_home)
BT=SDK/"build-tools/35.0.0"
ANDROID=SDK/"platforms/android-35/android.jar"
BUILD=pathlib.Path(os.environ.get("NOVA_BUILD_DIR",str(ROOT/"build")))
APK=pathlib.Path(os.environ.get("NOVA_APK_PATH",str(ROOT/"dist/Nova-Dock-0.1.0.apk")))
if BUILD.resolve()==ROOT or BUILD.resolve() in ROOT.parents:
    raise SystemExit("NOVA_BUILD_DIR must be a dedicated build directory.")
def run(*args): subprocess.run([str(a) for a in args],check=True,env={**os.environ,"JAVA_HOME":str(JAVA)})
if BUILD.exists(): shutil.rmtree(BUILD)
(BUILD/"classes").mkdir(parents=True)
(BUILD/"dex").mkdir()
(BUILD/"generated").mkdir()
run(BT/"aapt2","compile","--dir",ROOT/"res","-o",BUILD/"resources.zip")
run(BT/"aapt2","link","-o",BUILD/"unsigned.apk","-I",ANDROID,"--manifest",ROOT/"AndroidManifest.xml","--java",BUILD/"generated",BUILD/"resources.zip")
sources=list((ROOT/"src").rglob("*.java"))+list((BUILD/"generated").rglob("*.java"))
run(JAVA/"bin/javac","-encoding","UTF-8","-source","8","-target","8","-bootclasspath",str(ANDROID)+os.pathsep+str(BT/"core-lambda-stubs.jar"),"-d",BUILD/"classes",*sources)
with zipfile.ZipFile(BUILD/"classes.jar","w") as jar:
    for f in (BUILD/"classes").rglob("*.class"): jar.write(f,f.relative_to(BUILD/"classes"))
run(BT/"d8","--min-api","26","--lib",ANDROID,"--output",BUILD/"dex",BUILD/"classes.jar")
with zipfile.ZipFile(BUILD/"unsigned.apk","a",zipfile.ZIP_DEFLATED) as apk:
    apk.write(BUILD/"dex/classes.dex","classes.dex")
run(BT/"zipalign","-f","4",BUILD/"unsigned.apk",BUILD/"aligned.apk")
KEY=ROOT/"keys/local-development.p12"
if not KEY.exists():
    KEY.parent.mkdir(exist_ok=True)
    run(JAVA/"bin/keytool","-genkeypair","-keystore",KEY,"-storetype","PKCS12","-storepass","android","-keypass","android","-alias","nova-dock","-keyalg","RSA","-keysize","3072","-validity","10000","-dname","CN=Nova Dock local development")
APK.parent.mkdir(parents=True,exist_ok=True)
run(BT/"apksigner","sign","--ks",KEY,"--ks-pass","pass:android","--ks-key-alias","nova-dock","--out",APK,BUILD/"aligned.apk")
run(BT/"apksigner","verify","--verbose",APK)
run(JAVA/"bin/javac","-encoding","UTF-8","-d",BUILD/"tests",ROOT/"src/local/nova/dock/StableConnection.java",ROOT/"test/local/nova/dock/StableConnectionTest.java")
run(JAVA/"bin/java","-cp",BUILD/"tests","local.nova.dock.StableConnectionTest")
print(APK)
