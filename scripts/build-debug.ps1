param(
    [string]$SdkRoot = "D:\env\android-sdk"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$AppRoot = Join-Path $ProjectRoot "app"
$BuildRoot = Join-Path $ProjectRoot "build\manual-debug"
$ClassesDir = Join-Path $BuildRoot "classes"
$DexDir = Join-Path $BuildRoot "dex"
$GeneratedDir = Join-Path $BuildRoot "generated"
$BuildManifest = Join-Path $BuildRoot "AndroidManifest.xml"
$CompiledRes = Join-Path $BuildRoot "compiled-res.zip"
$UnsignedApk = Join-Path $BuildRoot "app-debug-unsigned.apk"
$DexApk = Join-Path $BuildRoot "app-debug-with-dex.apk"
$AlignedApk = Join-Path $BuildRoot "app-debug-aligned.apk"
$OutputApk = Join-Path $ProjectRoot "build\outputs\apk\debug\app-debug.apk"
$RootApk = Join-Path $ProjectRoot "SMS_Retre.apk"
$SourcesFile = Join-Path $BuildRoot "sources.txt"
$ClassesJar = Join-Path $BuildRoot "classes.jar"
$DebugKeystore = Join-Path $ProjectRoot "signing\sms-retre-debug.keystore"

$Aapt2 = Join-Path $SdkRoot "build-tools\35.0.0\aapt2.exe"
$D8 = Join-Path $SdkRoot "build-tools\35.0.0\d8.bat"
$Zipalign = Join-Path $SdkRoot "build-tools\35.0.0\zipalign.exe"
$ApkSigner = Join-Path $SdkRoot "build-tools\35.0.0\apksigner.bat"
$AndroidJar = Join-Path $SdkRoot "platforms\android-35\android.jar"

foreach ($Path in @($Aapt2, $D8, $Zipalign, $ApkSigner, $AndroidJar)) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing Android SDK file: $Path"
    }
}

function Invoke-Checked {
    param(
        [scriptblock]$Command
    )
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE"
    }
}

Remove-Item -LiteralPath $BuildRoot -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $ClassesDir, $DexDir, $GeneratedDir, (Split-Path $OutputApk -Parent) | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $DebugKeystore -Parent) | Out-Null

$ManifestText = Get-Content -LiteralPath (Join-Path $AppRoot "src\main\AndroidManifest.xml") -Raw
if ($ManifestText -notmatch "\bpackage=") {
    $ManifestText = $ManifestText -replace '<manifest xmlns:android="http://schemas.android.com/apk/res/android">', ("<manifest xmlns:android=""http://schemas.android.com/apk/res/android""" + "`n    package=""com.smsretre.app"">")
}
[System.IO.File]::WriteAllText($BuildManifest, $ManifestText, [System.Text.UTF8Encoding]::new($false))

Invoke-Checked { & $Aapt2 compile --dir (Join-Path $AppRoot "src\main\res") -o $CompiledRes }
Invoke-Checked {
    & $Aapt2 link `
        -o $UnsignedApk `
        -I $AndroidJar `
        --manifest $BuildManifest `
        --java $GeneratedDir `
        --min-sdk-version 26 `
        --target-sdk-version 35 `
        --version-code 2 `
        --version-name 0.2.0 `
        --auto-add-overlay `
        $CompiledRes
}

$SourceFiles = @()
$SourceFiles += Get-ChildItem -LiteralPath (Join-Path $AppRoot "src\main\java") -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
$SourceFiles += Get-ChildItem -LiteralPath $GeneratedDir -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
[System.IO.File]::WriteAllLines($SourcesFile, $SourceFiles, [System.Text.UTF8Encoding]::new($false))

Invoke-Checked {
    & javac `
        -encoding UTF-8 `
        -source 8 `
        -target 8 `
        -bootclasspath $AndroidJar `
        -classpath $AndroidJar `
        -d $ClassesDir `
        "@$SourcesFile"
}

Invoke-Checked { & jar --create --file $ClassesJar -C $ClassesDir . }
Invoke-Checked { & $D8 --min-api 26 --output $DexDir $ClassesJar }

Copy-Item -LiteralPath $UnsignedApk -Destination $DexApk -Force
Invoke-Checked { & jar --update --file $DexApk -C $DexDir classes.dex }
Invoke-Checked { & $Zipalign -f -p 4 $DexApk $AlignedApk }

if (-not (Test-Path -LiteralPath $DebugKeystore)) {
    Invoke-Checked {
        & keytool -genkeypair `
            -keystore $DebugKeystore `
            -storepass android `
            -alias androiddebugkey `
            -keypass android `
            -keyalg RSA `
            -keysize 2048 `
            -validity 10000 `
            -dname "CN=Android Debug,O=Android,C=US" | Out-Null
    }
}

Invoke-Checked {
    & $ApkSigner sign `
        --ks $DebugKeystore `
        --ks-pass pass:android `
        --key-pass pass:android `
        --out $OutputApk `
        $AlignedApk
}

Invoke-Checked { & $ApkSigner verify --verbose $OutputApk }
Copy-Item -LiteralPath $OutputApk -Destination $RootApk -Force
Write-Host "Built APK: $OutputApk"
Write-Host "Copied APK: $RootApk"
