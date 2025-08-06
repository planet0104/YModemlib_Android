set JAVA_HOME=C:\Program Files\Android\Android Studio2021\jre
set PATH=C:\Program Files\Android\Android Studio2021\jre\bin;%PATH%


$env:JAVA_HOME = "C:\Program Files\Android\Android Studio2021\jre";
$env:PATH = "C:\Program Files\Android\Android Studio2021\jre\bin;" + $env:PATH; java -version