param(
    [string]$ProjectZip = "",
    [string]$ProjectDir = "",
    [string]$VersionName = "3.5"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Step([string]$Text) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor DarkCyan
    Write-Host $Text -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor DarkCyan
}

function Run-Process([string]$FileName, [string]$Arguments, [switch]$AllowFailure) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FileName
    $psi.Arguments = $Arguments
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi

    if (-not $process.Start()) {
        throw "Impossibile avviare: $FileName"
    }

    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    $combined = ($stdout + "`r`n" + $stderr).Trim()
    if ($combined) { Write-Host $combined }

    if (-not $AllowFailure -and $process.ExitCode -ne 0) {
        throw ("Comando fallito con codice {0}: {1} {2}" -f $process.ExitCode, $FileName, $Arguments)
    }

    return @{
        ExitCode = $process.ExitCode
        Output = $combined
    }
}

function Download([string]$Url, [string]$Destination) {
    Write-Host "Scarico: $Url"
    Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
    if (-not (Test-Path $Destination)) {
        throw "Download non riuscito: $Destination"
    }
}

function Get-JavaVersionText([string]$JavaExePath) {
    $result = Run-Process $JavaExePath "-version"
    return $result.Output
}

function Find-Java17 {
    $list = @()

    if ($env:JAVA_HOME) {
        $list += (Join-Path $env:JAVA_HOME "bin\java.exe")
    }

    $javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $list += $javaCmd.Source
    }

    $list += @(
        "$env:ProgramFiles\Android\Android Studio\jbr\bin\java.exe",
        "$env:ProgramFiles\Android\Android Studio\jre\bin\java.exe"
    )

    if (Test-Path "$env:ProgramFiles\Microsoft") {
        $list += Get-ChildItem "$env:ProgramFiles\Microsoft" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName "bin\java.exe" }
    }

    if (Test-Path "$env:ProgramFiles\Eclipse Adoptium") {
        $list += Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName "bin\java.exe" }
    }

    foreach ($javaExePath in ($list | Select-Object -Unique)) {
        if (-not $javaExePath -or -not (Test-Path $javaExePath)) { continue }
        try {
            $versionText = Get-JavaVersionText $javaExePath
            if ($versionText -match 'version "17\.|openjdk version "17\.') {
                return (Split-Path (Split-Path $javaExePath -Parent) -Parent)
            }
        } catch {}
    }

    return $null
}

function Ensure-Java17([string]$ToolRoot) {
    $javaRoot = Find-Java17
    if ($javaRoot) {
        Write-Host "Java 17 trovato: $javaRoot" -ForegroundColor Green
        return $javaRoot
    }

    Step "Java 17 non trovato: preparo un JDK locale"

    $jdkContainer = Join-Path $ToolRoot "jdk17"
    $javaFound = Get-ChildItem $jdkContainer -Recurse -Filter java.exe -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\bin\\java\.exe$' } |
        Select-Object -First 1

    if (-not $javaFound) {
        Remove-Item $jdkContainer -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $jdkContainer | Out-Null

        $jdkZip = Join-Path $ToolRoot "jdk17.zip"
        Download "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" $jdkZip
        Expand-Archive -Path $jdkZip -DestinationPath $jdkContainer -Force
        Remove-Item $jdkZip -Force -ErrorAction SilentlyContinue

        $javaFound = Get-ChildItem $jdkContainer -Recurse -Filter java.exe |
            Where-Object { $_.FullName -match '\\bin\\java\.exe$' } |
            Select-Object -First 1
    }

    if (-not $javaFound) {
        throw "Non riesco a preparare Java 17."
    }

    return (Split-Path (Split-Path $javaFound.FullName -Parent) -Parent)
}

function Ensure-Gradle([string]$ToolRoot) {
    $gradleRoot = Join-Path $ToolRoot "gradle-8.9"
    $gradleBat = Join-Path $gradleRoot "bin\gradle.bat"

    if (Test-Path $gradleBat) {
        Write-Host "Gradle 8.9 già disponibile." -ForegroundColor Green
        return $gradleBat
    }

    Step "Scarico Gradle 8.9"

    $zip = Join-Path $ToolRoot "gradle-8.9-bin.zip"
    Download "https://services.gradle.org/distributions/gradle-8.9-bin.zip" $zip
    Expand-Archive -Path $zip -DestinationPath $ToolRoot -Force
    Remove-Item $zip -Force -ErrorAction SilentlyContinue

    if (-not (Test-Path $gradleBat)) {
        throw "Gradle 8.9 non installato correttamente."
    }

    return $gradleBat
}

function Find-AndroidSdk {
    $sdkCandidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:USERPROFILE\AppData\Local\Android\Sdk"
    ) | Where-Object { $_ }

    foreach ($sdkPath in ($sdkCandidates | Select-Object -Unique)) {
        if (-not (Test-Path $sdkPath)) { continue }

        $manager = Get-ChildItem (Join-Path $sdkPath "cmdline-tools") -Recurse -Filter sdkmanager.bat -ErrorAction SilentlyContinue |
            Select-Object -First 1

        if ($manager) {
            return @{ Root = $sdkPath; Manager = $manager.FullName }
        }
    }

    return $null
}

function Ensure-AndroidSdk([string]$ToolRoot) {
    $existing = Find-AndroidSdk
    if ($existing) {
        Write-Host "Android SDK trovato: $($existing.Root)" -ForegroundColor Green
        return $existing
    }

    Step "Preparo Android SDK locale"

    $sdkRoot = Join-Path $ToolRoot "android-sdk"
    $manager = Get-ChildItem (Join-Path $sdkRoot "cmdline-tools") -Recurse -Filter sdkmanager.bat -ErrorAction SilentlyContinue |
        Select-Object -First 1

    if (-not $manager) {
        $temp = Join-Path $ToolRoot "android-cmdline-temp"
        Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $temp | Out-Null
        New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null

        $zip = Join-Path $ToolRoot "commandlinetools-win.zip"
        Download "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip" $zip
        Expand-Archive -Path $zip -DestinationPath $temp -Force
        Remove-Item $zip -Force -ErrorAction SilentlyContinue

        $latest = Join-Path $sdkRoot "cmdline-tools\latest"
        Remove-Item $latest -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $latest | Out-Null
        Copy-Item (Join-Path $temp "cmdline-tools\*") $latest -Recurse -Force
        Remove-Item $temp -Recurse -Force

        $manager = Get-Item (Join-Path $latest "bin\sdkmanager.bat")
    }

    return @{ Root = $sdkRoot; Manager = $manager.FullName }
}

function Install-SdkPackages([string]$SdkRoot, [string]$SdkManagerBat) {
    Step "Preparo Android SDK 35"

    $env:ANDROID_SDK_ROOT = $SdkRoot
    $env:ANDROID_HOME = $SdkRoot

    # FIX v10:
    # niente pipe/for inline nel comando cmd.exe.
    # Creo invece un piccolo .cmd temporaneo e un file contenente
    # molte risposte "y", usato come STDIN per sdkmanager --licenses.
    # In questo modo funzionano anche percorsi Windows con spazi.
    $answerFile = Join-Path $SdkRoot "_successo_accept_licenses.txt"
    $setupCmd = Join-Path $SdkRoot "_successo_sdk_setup.cmd"

    $yesLines = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt 200; $i++) {
        $yesLines.Add("y")
    }
    [System.IO.File]::WriteAllLines(
        $answerFile,
        $yesLines.ToArray(),
        [System.Text.Encoding]::ASCII
    )

    $cmdLines = @(
        "@echo off",
        "setlocal",
        "call `"$SdkManagerBat`" --sdk_root=`"$SdkRoot`" --licenses < `"$answerFile`"",
        "if errorlevel 1 exit /b %errorlevel%",
        "call `"$SdkManagerBat`" --sdk_root=`"$SdkRoot`" `"platform-tools`" `"platforms;android-35`" `"build-tools;35.0.0`"",
        "exit /b %errorlevel%"
    )

    [System.IO.File]::WriteAllLines(
        $setupCmd,
        $cmdLines,
        [System.Text.Encoding]::ASCII
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/d /c _successo_sdk_setup.cmd"
    $psi.WorkingDirectory = $SdkRoot
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi

    if (-not $process.Start()) {
        throw "Impossibile avviare la preparazione dell'Android SDK."
    }

    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    if ($stdout) { Write-Host $stdout }
    if ($stderr) { Write-Host $stderr }

    $exitCode = $process.ExitCode

    Remove-Item $answerFile -Force -ErrorAction SilentlyContinue
    Remove-Item $setupCmd -Force -ErrorAction SilentlyContinue

    if ($exitCode -ne 0) {
        throw ("Preparazione Android SDK fallita con codice {0}." -f $exitCode)
    }

    $required = @(
        (Join-Path $SdkRoot "platform-tools\adb.exe"),
        (Join-Path $SdkRoot "platforms\android-35\android.jar"),
        (Join-Path $SdkRoot "build-tools\35.0.0\apksigner.bat")
    )

    foreach ($file in $required) {
        if (-not (Test-Path $file)) {
            throw "Android SDK incompleto: manca $file"
        }
    }

    Write-Host "Android SDK 35 pronto e licenze accettate." -ForegroundColor Green
}

function Resolve-AndroidProject([string]$BaseFolder, [string]$ZipArgument, [string]$DirArgument) {
    if ($DirArgument) {
        $resolved = (Resolve-Path $DirArgument).Path
        if (-not (Test-Path (Join-Path $resolved "settings.gradle"))) {
            throw "La cartella indicata non sembra un progetto Android."
        }
        return $resolved
    }

    if (-not $ZipArgument) {
        $candidate = Get-ChildItem $BaseFolder -Filter "SuccessoPlayer_Android*v3.5*LockScreenMedia*Source*.zip" -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

        if (-not $candidate) {
            throw "Non trovo SuccessoPlayer_Android_v3.5_LockScreenMedia_Source.zip accanto allo script."
        }

        $ZipArgument = $candidate.FullName
    }

    $resolvedZip = (Resolve-Path $ZipArgument).Path
    $working = Join-Path $BaseFolder "_SuccessoPlayer_build"

    Remove-Item $working -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $working | Out-Null
    Expand-Archive -Path $resolvedZip -DestinationPath $working -Force

    $settings = Get-ChildItem $working -Recurse -Filter settings.gradle -File | Select-Object -First 1
    if (-not $settings) {
        throw "settings.gradle non trovato nello ZIP."
    }

    return $settings.Directory.FullName
}

function Ensure-StableSigningKey([string]$StateRoot, [string]$JavaRoot) {
    Step "Preparo la firma stabile per gli aggiornamenti"

    New-Item -ItemType Directory -Force -Path $StateRoot | Out-Null

    $stableKey = Join-Path $StateRoot "successo_update.keystore"
    $androidDir = Join-Path $env:USERPROFILE ".android"
    $defaultKey = Join-Path $androidDir "debug.keystore"

    New-Item -ItemType Directory -Force -Path $androidDir | Out-Null

    if (-not (Test-Path $stableKey)) {
        if (Test-Path $defaultKey) {
            Copy-Item $defaultKey $stableKey -Force
            Write-Host "Ho adottato la chiave Android già presente sul PC." -ForegroundColor Green
            Write-Host "Questo aumenta la probabilità che l'APK aggiorni direttamente l'app già installata." -ForegroundColor Green
        }
        else {
            $keytool = Join-Path $JavaRoot "bin\keytool.exe"
            $args = '-genkeypair -v -keystore "' + $stableKey + '" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"'
            Run-Process $keytool $args | Out-Null
            Write-Host "Creata nuova chiave stabile di Successo Player." -ForegroundColor Green
        }
    }

    # Gradle debug usa questa posizione. La sincronizziamo SEMPRE
    # con la chiave stabile conservata in LocalAppData.
    Copy-Item $stableKey $defaultKey -Force

    Write-Host "Firma persistente: $stableKey" -ForegroundColor DarkGray
    return $stableKey
}

function Get-NextVersionCode([string]$ProjectRoot, [string]$StateRoot) {
    $appGradle = Join-Path $ProjectRoot "app\build.gradle"
    $content = Get-Content $appGradle -Raw

    $match = [regex]::Match($content, 'versionCode\s+(\d+)')
    if (-not $match.Success) {
        throw "versionCode non trovato in app\build.gradle"
    }

    $sourceCode = [int]$match.Groups[1].Value
    $stateFile = Join-Path $StateRoot "ultimo_version_code.txt"

    if (Test-Path $stateFile) {
        $stateText = (Get-Content $stateFile -Raw).Trim()
        $lastCode = 0
        if ([int]::TryParse($stateText, [ref]$lastCode)) {
            if ($lastCode -ge $sourceCode) {
                return ($lastCode + 1)
            }
        }
    }

    return $sourceCode
}

function Set-AppVersion([string]$ProjectRoot, [int]$Code, [string]$Name) {
    $appGradle = Join-Path $ProjectRoot "app\build.gradle"
    $text = Get-Content $appGradle -Raw

    $text = [regex]::Replace($text, 'versionCode\s+\d+', "versionCode $Code")
    $text = [regex]::Replace($text, 'versionName\s+[''"][^''"]+[''"]', "versionName '$Name'")

    [System.IO.File]::WriteAllText($appGradle, $text, [System.Text.UTF8Encoding]::new($false))
}

$scriptFolder = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolFolder = Join-Path $scriptFolder ".successo-build-tools"
$stateRoot = Join-Path $env:LOCALAPPDATA "SuccessoPlayerBuild"
$logPath = Join-Path $scriptFolder "build_successo_v23.log"

New-Item -ItemType Directory -Force -Path $toolFolder | Out-Null
New-Item -ItemType Directory -Force -Path $stateRoot | Out-Null

try {
    Write-Host ""
    Write-Host "SUCCESSO PLAYER - BUILD APK AUTOMATICA v23" -ForegroundColor Green
    Write-Host "Modalità aggiornamento: STESSO PACKAGE + FIRMA PERSISTENTE + VERSIONCODE CRESCENTE" -ForegroundColor Cyan
    Write-Host ""

    "=== Successo Player build v23 ===" | Set-Content $logPath -Encoding UTF8
    "Data: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" | Add-Content $logPath

    Step "1/7 - Individuo il progetto"
    $projectRoot = Resolve-AndroidProject $scriptFolder $ProjectZip $ProjectDir
    Write-Host "Progetto: $projectRoot" -ForegroundColor Green

    Step "2/7 - Java 17"
    $javaRoot = Ensure-Java17 $toolFolder
    $env:JAVA_HOME = $javaRoot
    $env:Path = (Join-Path $javaRoot "bin") + ";" + $env:Path
    $javaText = Get-JavaVersionText (Join-Path $javaRoot "bin\java.exe")
    $javaText | Tee-Object -FilePath $logPath -Append

    Step "3/7 - Firma stabile"
    $stableKey = Ensure-StableSigningKey $stateRoot $javaRoot

    Step "4/7 - Gradle e Android SDK"
    $gradleBat = Ensure-Gradle $toolFolder
    $sdkInfo = Ensure-AndroidSdk $toolFolder
    Install-SdkPackages $sdkInfo.Root $sdkInfo.Manager

    $localProperties = Join-Path $projectRoot "local.properties"
    $escapedSdkPath = $sdkInfo.Root.Replace("\", "\\")
    [System.IO.File]::WriteAllText(
        $localProperties,
        "sdk.dir=$escapedSdkPath`r`n",
        [System.Text.UTF8Encoding]::new($false)
    )

    Step "5/7 - Incremento versione per consentire l'aggiornamento"
    $versionCode = Get-NextVersionCode $projectRoot $stateRoot
    Set-AppVersion $projectRoot $versionCode $VersionName
    Write-Host "Versione: $VersionName  versionCode: $versionCode" -ForegroundColor Green

    Step "6/7 - Compilo APK"

    # FIX v11:
    # evito completamente le virgolette annidate dentro:
    #   cmd.exe /d /s /c "call ""...gradle.bat"" ..."
    # che su alcuni Windows produce "Sintassi del comando errata".
    #
    # Creo un .cmd temporaneo nella cartella del progetto e lancio
    # semplicemente quel file come comando.
    $gradleCmdFile = Join-Path $projectRoot "_successo_gradle_build.cmd"
    $gradleOutputFile = Join-Path $projectRoot "_successo_gradle_build.log"

    $gradleCmdLines = @(
        "@echo off",
        "call `"$gradleBat`" --no-daemon clean :app:assembleDebug --stacktrace > `"$gradleOutputFile`" 2>&1",
        "exit /b %errorlevel%"
    )

    [System.IO.File]::WriteAllLines(
        $gradleCmdFile,
        $gradleCmdLines,
        [System.Text.Encoding]::ASCII
    )

    $psiGradle = New-Object System.Diagnostics.ProcessStartInfo
    $psiGradle.FileName = "cmd.exe"
    $psiGradle.Arguments = "/d /c _successo_gradle_build.cmd"
    $psiGradle.WorkingDirectory = $projectRoot
    $psiGradle.UseShellExecute = $false
    $psiGradle.CreateNoWindow = $true
    $psiGradle.RedirectStandardOutput = $true
    $psiGradle.RedirectStandardError = $true

    $processGradle = New-Object System.Diagnostics.Process
    $processGradle.StartInfo = $psiGradle

    if (-not $processGradle.Start()) {
        throw "Impossibile avviare Gradle."
    }

    $gradleWrapperStdout = $processGradle.StandardOutput.ReadToEnd()
    $gradleWrapperStderr = $processGradle.StandardError.ReadToEnd()
    $processGradle.WaitForExit()

    if ($gradleWrapperStdout) { Write-Host $gradleWrapperStdout }
    if ($gradleWrapperStderr) { Write-Host $gradleWrapperStderr }

    if (Test-Path $gradleOutputFile) {
        $gradleOutput = Get-Content $gradleOutputFile -Raw
        Write-Host $gradleOutput
        Add-Content $logPath $gradleOutput
    }

    $gradleExitCode = $processGradle.ExitCode

    Remove-Item $gradleCmdFile -Force -ErrorAction SilentlyContinue
    Remove-Item $gradleOutputFile -Force -ErrorAction SilentlyContinue

    if ($gradleExitCode -ne 0) {
        throw ("Gradle ha restituito codice errore {0}" -f $gradleExitCode)
    }

    $builtApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $builtApk)) {
        throw "APK compilato non trovato."
    }

    Step "7/7 - Verifico firma e preparo aggiornamento"
    $signer = Join-Path $sdkInfo.Root "build-tools\35.0.0\apksigner.bat"

    if (Test-Path $signer) {
        # Stesso approccio robusto usato per Gradle:
        # file .cmd temporaneo, niente labirinto di virgolette.
        $verifyCmdFile = Join-Path $projectRoot "_successo_verify_apk.cmd"
        $verifyOutputFile = Join-Path $projectRoot "_successo_verify_apk.log"

        $verifyCmdLines = @(
            "@echo off",
            "call `"$signer`" verify --verbose --print-certs `"$builtApk`" > `"$verifyOutputFile`" 2>&1",
            "exit /b %errorlevel%"
        )

        [System.IO.File]::WriteAllLines(
            $verifyCmdFile,
            $verifyCmdLines,
            [System.Text.Encoding]::ASCII
        )

        $psiVerify = New-Object System.Diagnostics.ProcessStartInfo
        $psiVerify.FileName = "cmd.exe"
        $psiVerify.Arguments = "/d /c _successo_verify_apk.cmd"
        $psiVerify.WorkingDirectory = $projectRoot
        $psiVerify.UseShellExecute = $false
        $psiVerify.CreateNoWindow = $true
        $psiVerify.RedirectStandardOutput = $true
        $psiVerify.RedirectStandardError = $true

        $processVerify = New-Object System.Diagnostics.Process
        $processVerify.StartInfo = $psiVerify

        if (-not $processVerify.Start()) {
            throw "Impossibile avviare apksigner."
        }

        $verifyWrapperStdout = $processVerify.StandardOutput.ReadToEnd()
        $verifyWrapperStderr = $processVerify.StandardError.ReadToEnd()
        $processVerify.WaitForExit()

        if ($verifyWrapperStdout) { Write-Host $verifyWrapperStdout }
        if ($verifyWrapperStderr) { Write-Host $verifyWrapperStderr }

        if (Test-Path $verifyOutputFile) {
            $verifyOutput = Get-Content $verifyOutputFile -Raw
            Write-Host $verifyOutput
            Add-Content $logPath $verifyOutput
        }

        $verifyExitCode = $processVerify.ExitCode

        Remove-Item $verifyCmdFile -Force -ErrorAction SilentlyContinue
        Remove-Item $verifyOutputFile -Force -ErrorAction SilentlyContinue

        if ($verifyExitCode -ne 0) {
            throw ("Verifica firma APK fallita, codice {0}." -f $verifyExitCode)
        }
    }

    $outputName = "SuccessoPlayer_Android_v{0}_build{1}.apk" -f $VersionName, $versionCode
    $outputApk = Join-Path $scriptFolder $outputName
    Copy-Item $builtApk $outputApk -Force

    # Aggiorna il contatore SOLO dopo build riuscita.
    Set-Content (Join-Path $stateRoot "ultimo_version_code.txt") $versionCode -Encoding ASCII

    Write-Host ""
    Write-Host "BUILD COMPLETATA." -ForegroundColor Green
    Write-Host "APK: $outputApk" -ForegroundColor Green
    Write-Host ""
    Write-Host "Le prossime build useranno la STESSA firma e un versionCode più alto." -ForegroundColor Cyan
    Write-Host "Android le vedrà come aggiornamenti della stessa app." -ForegroundColor Cyan
    Write-Host ""
    Write-Host "IMPORTANTE: conserva un backup di:" -ForegroundColor Yellow
    Write-Host $stableKey -ForegroundColor Yellow
    Write-Host ""

    Start-Process explorer.exe "/select,`"$outputApk`""
    exit 0
}
catch {
    $message = $_.Exception.Message
    $position = $_.InvocationInfo.PositionMessage

    Write-Host ""
    Write-Host "ERRORE DI COMPILAZIONE" -ForegroundColor Red
    Write-Host $message -ForegroundColor Red
    if ($position) { Write-Host $position -ForegroundColor DarkYellow }

    Add-Content $logPath ""
    Add-Content $logPath "ERRORE:"
    Add-Content $logPath $message
    Add-Content $logPath $position

    Write-Host ""
    Write-Host "Log: $logPath" -ForegroundColor Yellow
    exit 1
}
