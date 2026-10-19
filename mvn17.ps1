param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArguments = @('clean','test'))
$jdkPath = Join-Path $env:USERPROFILE '.jdks\ms-17.0.20.1'
$mavenPath = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd'
$previousJavaHome = $env:JAVA_HOME
$code = 1
Push-Location -LiteralPath $PSScriptRoot
try {
 $env:JAVA_HOME = $jdkPath
 & $mavenPath @MavenArguments
 $code = $LASTEXITCODE
} finally { $env:JAVA_HOME = $previousJavaHome; Pop-Location }
exit $code
