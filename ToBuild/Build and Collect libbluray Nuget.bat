@echo off

REM Set Nuget package version
set version="%1"
if %version%=="" (
set /p version="Enter version for package(x.x.x) : "
)

call :sub >Collect_And_Build.log
:sub

Set GIT_ROOT=..
Set NugetPackages=%GIT_ROOT%\Packages
set LibblurayJAR="%GIT_ROOT%\src\libbluray\bdj\build.xml"
set TARGET=Release
set PKG=MediaPortal.libbluray
set MSBUILD_VS_PRM2=""
set MSBUILD_TOOL_PRM=""
rem FORCE Toolversion and VisualStudio version
set MSBUILD_TOOL_PRM=12.0
set MSBUILD_VS_PRM2=14.0
set MSBUILDTREATALLTOOLSVERSIONSASCURRENT=1

REM Set MSbuild location
set                   MB="%ProgramFiles(x86)%\Microsoft Visual Studio\2019\Community\MSBuild\Current\Bin\MSBuild.exe"
rem VS2019 not supported yet by project
REM if exist %MB% (if %MSBUILD_TOOL_PRM%=="" set MSBUILD_TOOL_PRM=14.0)
REM if exist %MB% (if %MSBUILD_VS_PRM2%=="" set MSBUILD_VS_PRM2=16.0)

if not exist %MB% set MB="%ProgramFiles(x86)%\Microsoft Visual Studio\2017\Community\MSBuild\15.0\Bin\MSBUILD.exe"
if exist %MB% (if %MSBUILD_TOOL_PRM%=="" set MSBUILD_TOOL_PRM=12.0)
if exist %MB% (if %MSBUILD_VS_PRM2%=="" set MSBUILD_VS_PRM2=15.0)

if not exist %MB% set MB="%ProgramFiles(x86)%\MSBuild\14.0\Bin\MSBUILD.exe"
if exist %MB% (if %MSBUILD_TOOL_PRM%=="" set MSBUILD_TOOL_PRM=12.0)
if exist %MB% (if %MSBUILD_VS_PRM2%=="" set MSBUILD_VS_PRM2=14.0)

rem set BUILD_OPTS=" /t:Rebuild"
set BUILD_OPTS=

rem %MB% Nuget.targets /target:DownloadNuGet
@%MB% RestorePackages.targets > Nuget_Restore.log

IF %MSBUILD_TOOL_PRM%=="" (
set _tv=""
) ELSE (
set _tv="-tv:%MSBUILD_TOOL_PRM%"
)

rem BUILD JAR FILES
call .\Build_BD_Java.bat

ECHO %MB% Tool_verson:%MSBUILD_TOOL_PRM% VisualStudio_version:%MSBUILD_VS_PRM2%
%MB% %GIT_ROOT%\libbluray.sln %_tv% /P:VisualStudioVersion=%MSBUILD_VS_PRM2% /P:Configuration=%TARGET% /P:Platform=Win32 %BUILD_OPTS% || exit /b 1

goto copyfiles_Release

:debug_build

set TARGET=Debug
%MB% %GIT_ROOT%\libbluray.sln %_tv% /P:VisualStudioVersion=%MSBUILD_VS_PRM2% /P:Configuration=%TARGET% /P:Platform=Win32 %BUILD_OPTS% || exit /b 1
rem %MB% ..\libbluray.sln /P:Configuration=%TARGET% %MSBUILD_TOOL_PRM% /P:Platform=x64 %BUILD_OPTS% || exit /b 1


rem "lib" (direct references)
:copyfiles_Debug
xcopy "%GIT_ROOT%\bin_Win32d\*.*" "_libbluray\%PKG%\references\runtimes\%TARGET%\" /R /Y  || exit /b 1

GOTO copyfiles

:copyfiles_Release

rem nuget update -self
xcopy "%GIT_ROOT%\bin_Win32\*.*" "_libbluray\%PKG%\references\runtimes\%TARGET%\" /R /Y  || exit /b 1

:copyfiles
rem path compatible with %target%
xcopy "%GIT_ROOT%\3rd_party\freetype2\objs\Win32\%TARGET%\freetype.dll" "_libbluray\%PKG%\references\runtimes\%TARGET%\" /R /Y  || exit /b 1

rem "references\"
xcopy "%GIT_ROOT%\src\.libs\libbluray-.jar" "_libbluray\%PKG%\references\runtimes\" /R /Y  || exit /b 1
xcopy "%GIT_ROOT%\src\.libs\libbluray-awt-.jar" "_libbluray\%PKG%\references\runtimes\" /R /Y  || exit /b 1

rem "build\lib"
xcopy "%GIT_ROOT%\includes\*.*" "_libbluray\%PKG%\build\lib\includes\" /R /Y  || exit /b 1
xcopy "%GIT_ROOT%\src\libbluray\*.*" "_libbluray\%PKG%\build\lib\libbluray\" /R /Y  || exit /b 1
xcopy "%GIT_ROOT%\src\libbluray\decoders\*.*" "_libbluray\%PKG%\build\lib\libbluray\decoders\" /R /Y  || exit /b 1
xcopy "%GIT_ROOT%\src\libbluray\bdnav\*.*" "_libbluray\%PKG%\build\lib\libbluray\bdnav\" /R /Y  || exit /b 1


rem "references\SetupTv"


if %TARGET%==Release GOTO debug_build

nuget pack .\_libbluray\MediaPortal.Libbluray\MediaPortal.libbluray.nuspec -Version %version%
exit /b