
@REM # 1. 检查 JDK 17 的文件是否存在
Test-Path "F:\ayd\jdk-17\bin\java.exe"
Test-Path "F:\ayd\jdk-17\bin\javac.exe"

# 2. 仅修改当前 PowerShell 会话的环境变量
$env:JAVA_HOME = "F:\ayd\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 3. 验证 Java 运行时和编译器
java -version
javac -version

# 4. 验证 Maven 实际使用的 Java
.\mvnw.cmd -version

@REM 直接执行，上面是用来看的
$env:JAVA_HOME = "F:\ayd\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test