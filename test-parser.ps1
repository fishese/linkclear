$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force app/build/parser-tests | Out-Null
    javac -d app/build/parser-tests app/src/main/java/app/linkstripper/LinkCleaner.java app/src/main/java/app/linkstripper/LinkResolver.java app/src/test/java/app/linkstripper/ParserTest.java app/src/test/java/app/linkstripper/ResolverProbe.java
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
    java -cp app/build/parser-tests app.linkstripper.ParserTest
    if ($LASTEXITCODE -ne 0) { throw 'Parser tests failed' }
} finally { Pop-Location }
