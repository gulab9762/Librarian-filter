param(
    [switch]$Dev,            # Use Gradle development server (:neoforge:runServer)
    [switch]$Clean,          # Wipe test_server directory and reinstall from scratch
    [switch]$NoRun,          # Only build and setup the server, do not start it
    [string]$JvmArgs = "-Xmx4G -Xms2G"
)

$ErrorActionPreference = "Stop"
$WorkspaceRoot = $PSScriptRoot

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " NeoForge Dedicated Server Test Utility " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# Read properties from gradle.properties
$propsFile = Join-Path $WorkspaceRoot "gradle.properties"
$neoforgeVersion = "21.1.242"
$mcVersion = "1.21.1"

if (Test-Path $propsFile) {
    Get-Content $propsFile | ForEach-Object {
        if ($_ -match "^\s*neoforge_version\s*=\s*(.+)$") {
            $neoforgeVersion = $matches[1].Trim()
        }
        if ($_ -match "^\s*minecraft_version\s*=\s*(.+)$") {
            $mcVersion = $matches[1].Trim()
        }
    }
}

Write-Host "Minecraft Version: $mcVersion" -ForegroundColor Yellow
Write-Host "NeoForge Version:  $neoforgeVersion" -ForegroundColor Yellow

# Mode 1: Gradle Dev Server
if ($Dev) {
    Write-Host "`n[1/1] Launching dev dedicated server via Gradle..." -ForegroundColor Green
    Set-Location $WorkspaceRoot
    & .\gradlew :neoforge:runServer --nogui
    exit $LASTEXITCODE
}

# Mode 2: Standalone Production Server
$serverDir = Join-Path $WorkspaceRoot "test_server"
$modsDir = Join-Path $serverDir "mods"

if ($Clean -and (Test-Path $serverDir)) {
    Write-Host "`nCleaning test_server directory..." -ForegroundColor Yellow
    Remove-Item -Path $serverDir -Recurse -Force
}

if (-not (Test-Path $serverDir)) {
    New-Item -ItemType Directory -Path $serverDir | Out-Null
}
if (-not (Test-Path $modsDir)) {
    New-Item -ItemType Directory -Path $modsDir | Out-Null
}

# 1. Build the mod
Write-Host "`n[1/4] Building latest mod jar..." -ForegroundColor Green
Set-Location $WorkspaceRoot
& .\gradlew :neoforge:build
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to build the NeoForge mod."
}

# Find built jar (skip -sources.jar)
$jarFile = Get-ChildItem -Path (Join-Path $WorkspaceRoot "neoforge\build\libs") -Filter "*.jar" | 
    Where-Object { $_.Name -notmatch "-sources" } | 
    Sort-Object LastWriteTime -Descending | 
    Select-Object -First 1

if (-not $jarFile) {
    Write-Error "Could not find built mod jar in neoforge/build/libs/!"
}

Write-Host "Built jar: $($jarFile.Name)" -ForegroundColor Cyan

# Copy jar to mods directory (remove older versions of our mod first)
Get-ChildItem -Path $modsDir -Filter "librarian-filter-neoforge*.jar" | Remove-Item -Force
Copy-Item -Path $jarFile.FullName -Destination (Join-Path $modsDir $jarFile.Name) -Force
Write-Host "Copied $($jarFile.Name) -> test_server\mods\" -ForegroundColor Cyan

# 2. Check if NeoForge server is installed
$runBat = Join-Path $serverDir "run.bat"
if (-not (Test-Path $runBat)) {
    Write-Host "`n[2/4] Downloading NeoForge installer..." -ForegroundColor Green
    $installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoforgeVersion/neoforge-$neoforgeVersion-installer.jar"
    $installerPath = Join-Path $serverDir "neoforge-installer.jar"
    
    Write-Host "Downloading from $installerUrl..."
    Invoke-WebRequest -Uri $installerUrl -OutFile $installerPath -UseBasicParsing
    
    Write-Host "`n[3/4] Installing NeoForge Dedicated Server (this may take 1-2 minutes)..." -ForegroundColor Green
    Set-Location $serverDir
    & java -jar $installerPath --install-server
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Server installation failed."
    }
    Remove-Item $installerPath -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "`n[2/4] NeoForge server already installed." -ForegroundColor Green
    Write-Host "[3/4] Skipping installer step." -ForegroundColor DarkGray
}

# 3. Accept EULA
$eulaFile = Join-Path $serverDir "eula.txt"
Set-Content -Path $eulaFile -Value "eula=true" -Force
Write-Host "Ensured eula=true in $eulaFile" -ForegroundColor Cyan

# Configure server.properties for test if not existing
$serverProps = Join-Path $serverDir "server.properties"
if (-not (Test-Path $serverProps)) {
    @"
online-mode=false
motd=Librarian Filter Test Server
max-players=10
enable-rcon=false
sync-chunk-writes=true
gamemode=creative
difficulty=peaceful
spawn-protection=0
"@ | Out-File -FilePath $serverProps -Encoding utf8
}

if ($NoRun) {
    Write-Host "`nServer prepared successfully in $serverDir. Run without -NoRun to start." -ForegroundColor Green
    exit 0
}

# 4. Start Server
Write-Host "`n[4/4] Starting dedicated server..." -ForegroundColor Green
Write-Host "Type 'stop' in the server console to shut it down cleanly.`n" -ForegroundColor Yellow

Set-Location $serverDir
& .\run.bat --nogui
