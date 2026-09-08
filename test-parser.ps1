$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force app/build/parser-tests | Out-Null
    javac -d app/build/parser-tests app/src/main/java/app/linkstripper/LinkCleaner.java app/src/main/java/app/linkstripper/LinkResolver.java app/src/main/java/app/linkstripper/SiteRule.java app/src/main/java/app/linkstripper/UrlInspector.java app/src/main/java/app/linkstripper/UpdateChecker.java app/src/test/java/app/linkstripper/ParserTest.java app/src/test/java/app/linkstripper/ResolverProbe.java app/src/test/java/app/linkstripper/SiteRuleTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
    java -cp app/build/parser-tests app.linkstripper.ParserTest
    if ($LASTEXITCODE -ne 0) { throw 'Parser tests failed' }
    java -cp app/build/parser-tests app.linkstripper.SiteRuleTest
    if ($LASTEXITCODE -ne 0) { throw 'Custom rule tests failed' }
} finally { Pop-Location }
