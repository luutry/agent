$base = "D:\project\luutry-agent"
$mainBase = "$base\src\main\java\com\luutry\agent"
$testBase = "$base\src\test\java\com\luutry\agent"

# Module mapping: core subpackage -> target top-level package
$modules = @{
    "observability"  = "observability"
    "security"       = "security"
    "tool"           = "tool"
    "memory"         = "memory"
    "rag"            = "rag"
    "preprocessor"   = "preprocessor"
    "planner"        = "planner"
    "critique"       = "critique"
    "context"        = "aggregate"  # package name change!
}

foreach ($srcPkg in $modules.Keys) {
    $dstPkg = $modules[$srcPkg]
    $srcMainDir = "$mainBase\core\$srcPkg"
    $dstMainDir = "$mainBase\$dstPkg"
    $srcTestDir = "$testBase\core\$srcPkg"
    $dstTestDir = "$testBase\$dstPkg"

    # Move main source files
    if (Test-Path $srcMainDir) {
        if (-not (Test-Path $dstMainDir)) {
            New-Item -ItemType Directory -Path $dstMainDir -Force | Out-Null
        }
        # Copy all subdirectories and files
        Get-ChildItem -Path $srcMainDir -Recurse | ForEach-Object {
            $relPath = $_.FullName.Substring($srcMainDir.Length)
            $targetPath = $dstMainDir + $relPath
            if ($_.PSIsContainer) {
                if (-not (Test-Path $targetPath)) {
                    New-Item -ItemType Directory -Path $targetPath -Force | Out-Null
                }
            } else {
                $targetDir = Split-Path -Parent $targetPath
                if (-not (Test-Path $targetDir)) {
                    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
                }
                Copy-Item $_.FullName -Destination $targetPath -Force
            }
        }
        Write-Host "Moved main: core/$srcPkg -> $dstPkg"
    }

    # Move test source files
    if (Test-Path $srcTestDir) {
        if (-not (Test-Path $dstTestDir)) {
            New-Item -ItemType Directory -Path $dstTestDir -Force | Out-Null
        }
        Get-ChildItem -Path $srcTestDir -Recurse | ForEach-Object {
            $relPath = $_.FullName.Substring($srcTestDir.Length)
            $targetPath = $dstTestDir + $relPath
            if ($_.PSIsContainer) {
                if (-not (Test-Path $targetPath)) {
                    New-Item -ItemType Directory -Path $targetPath -Force | Out-Null
                }
            } else {
                $targetDir = Split-Path -Parent $targetPath
                if (-not (Test-Path $targetDir)) {
                    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
                }
                Copy-Item $_.FullName -Destination $targetPath -Force
            }
        }
        Write-Host "Moved test: core/$srcPkg -> $dstPkg"
    }
}

# Now update package declarations and import statements in ALL java files
Write-Host "`nUpdating package/import in all Java files..."

$allJavaFiles = Get-ChildItem -Path "$base\src" -Recurse -Filter "*.java"

foreach ($file in $allJavaFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $original = $content

    foreach ($srcPkg in $modules.Keys) {
        $dstPkg = $modules[$srcPkg]
        # Replace package references (both in package declarations and import statements)
        $content = $content -replace "com\.luutry\.agent\.core\.$srcPkg", "com.luutry.agent.$dstPkg"
    }

    if ($content -ne $original) {
        Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
    }
}

Write-Host "Package/import updates complete."

# Delete old core directories
Write-Host "`nCleaning up old core directories..."
$coreMainDir = "$mainBase\core"
$coreTestDir = "$testBase\core"

if (Test-Path $coreMainDir) {
    Remove-Item -Path $coreMainDir -Recurse -Force
    Write-Host "Deleted: $coreMainDir"
}
if (Test-Path $coreTestDir) {
    Remove-Item -Path $coreTestDir -Recurse -Force
    Write-Host "Deleted: $coreTestDir"
}

# Also clean up empty observability top-level dir (it was empty before)
$emptyObsDir = "$mainBase\observability\log"
if (Test-Path "$mainBase\observability\log") {
    # Check if it's empty (only has old empty dirs)
    $files = Get-ChildItem -Path "$mainBase\observability" -Recurse -File
    if ($files.Count -eq 0) {
        Write-Host "Note: observability had pre-existing empty dirs, now populated from core"
    }
}

Write-Host "`nDone! Run 'mvn test' to verify."

