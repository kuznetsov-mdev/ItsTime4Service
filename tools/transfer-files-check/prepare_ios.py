from pathlib import Path
import json
import plistlib

ROOT = Path(__file__).resolve().parents[2]
WORK = Path("/private/tmp/it4s-transfer-ios-check")
PROJECT = WORK / "TransferFilesCheck.xcodeproj"
PROJECT.mkdir(parents=True, exist_ok=True)
objects = {}
def ident(n): return f"{n:024X}"
def obj(n, isa, **values):
    objects[ident(n)] = dict(isa=isa, **values)
    return ident(n)
def source(n, path, kind): return obj(n, "PBXFileReference", path=str(path), sourceTree="<absolute>", lastKnownFileType=kind)
main = source(10, ROOT / "tools/transfer-files-check/ios/Main.swift", "sourcecode.swift")
test = source(11, ROOT / "tools/transfer-files-check/ios/TransferFilesCheckUITests.swift", "sourcecode.swift")
plain = source(12, ROOT / "androidApp/src/transferCheck/assets/Plain.it4s", "file")
protected = source(13, ROOT / "androidApp/src/transferCheck/assets/Protected.it4s", "file")
framework = source(14, ROOT / "shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework", "wrapper.framework")
app = obj(15, "PBXFileReference", path="TransferFilesCheck.app", sourceTree="BUILT_PRODUCTS_DIR", explicitFileType="wrapper.application")
ui = obj(16, "PBXFileReference", path="TransferFilesCheckUITests.xctest", sourceTree="BUILT_PRODUCTS_DIR", explicitFileType="wrapper.cfbundle")
obj(20,"PBXBuildFile",fileRef=main);obj(21,"PBXBuildFile",fileRef=test);obj(22,"PBXBuildFile",fileRef=plain);obj(23,"PBXBuildFile",fileRef=protected);obj(24,"PBXBuildFile",fileRef=framework)
obj(30,"PBXSourcesBuildPhase",buildActionMask=2147483647,files=[ident(20)],runOnlyForDeploymentPostprocessing=0)
obj(31,"PBXResourcesBuildPhase",buildActionMask=2147483647,files=[ident(22),ident(23)],runOnlyForDeploymentPostprocessing=0)
obj(32,"PBXFrameworksBuildPhase",buildActionMask=2147483647,files=[ident(24)],runOnlyForDeploymentPostprocessing=0)
obj(33,"PBXSourcesBuildPhase",buildActionMask=2147483647,files=[ident(21)],runOnlyForDeploymentPostprocessing=0)
obj(34,"PBXFrameworksBuildPhase",buildActionMask=2147483647,files=[],runOnlyForDeploymentPostprocessing=0)
obj(40,"PBXGroup",children=[main,test,plain,protected,framework,ident(41)],sourceTree="<group>")
obj(41,"PBXGroup",children=[app,ui],name="Products",sourceTree="<group>")
shared = dict(ARCHS="arm64",ONLY_ACTIVE_ARCH="YES",EXCLUDED_ARCHS="x86_64",SDKROOT="iphonesimulator",SUPPORTED_PLATFORMS="iphonesimulator",IPHONEOS_DEPLOYMENT_TARGET="18.0",SWIFT_VERSION="5.0",CODE_SIGNING_ALLOWED="YES",CODE_SIGN_IDENTITY="-",CODE_SIGNING_REQUIRED="NO",DEVELOPMENT_TEAM="",CLANG_ENABLE_MODULES="YES",TARGETED_DEVICE_FAMILY="1",ALWAYS_SEARCH_USER_PATHS="NO")
obj(50,"XCBuildConfiguration",name="Debug",buildSettings=shared)
obj(51,"XCBuildConfiguration",name="Debug",buildSettings=dict(PRODUCT_NAME="TransferFilesCheck",PRODUCT_BUNDLE_IDENTIFIER="ru.kuznetsov.itstime4service.filescheck",INFOPLIST_FILE=str(ROOT/"tools/transfer-files-check/ios/Info.plist"),FRAMEWORK_SEARCH_PATHS=[str(ROOT/"shared/build/bin/iosSimulatorArm64/debugFramework")],OTHER_LDFLAGS=["$(inherited)","-framework","Shared","-lsqlite3","-lc++"],LD_RUNPATH_SEARCH_PATHS=["$(inherited)","@executable_path/Frameworks"]))
obj(52,"XCBuildConfiguration",name="Debug",buildSettings=dict(PRODUCT_NAME="TransferFilesCheckUITests",PRODUCT_BUNDLE_IDENTIFIER="ru.kuznetsov.itstime4service.filescheck.uitests",GENERATE_INFOPLIST_FILE="YES",TEST_TARGET_NAME="TransferFilesCheck"))
for n,c in [(60,50),(61,51),(62,52)]: obj(n,"XCConfigurationList",buildConfigurations=[ident(c)],defaultConfigurationIsVisible=0,defaultConfigurationName="Debug")
obj(70,"PBXNativeTarget",name="TransferFilesCheck",productName="TransferFilesCheck",productType="com.apple.product-type.application",productReference=app,buildConfigurationList=ident(61),buildPhases=[ident(30),ident(32),ident(31)],buildRules=[],dependencies=[])
obj(71,"PBXNativeTarget",name="TransferFilesCheckUITests",productName="TransferFilesCheckUITests",productType="com.apple.product-type.bundle.ui-testing",productReference=ui,buildConfigurationList=ident(62),buildPhases=[ident(33),ident(34)],buildRules=[],dependencies=[ident(73)])
obj(72,"PBXContainerItemProxy",containerPortal=ident(1),proxyType=1,remoteGlobalIDString=ident(70),remoteInfo="TransferFilesCheck")
obj(73,"PBXTargetDependency",target=ident(70),targetProxy=ident(72))
obj(1,"PBXProject",attributes={"LastUpgradeCheck":"1620","TargetAttributes":{ident(70):{"CreatedOnToolsVersion":"16.2"},ident(71):{"CreatedOnToolsVersion":"16.2","TestTargetID":ident(70)}}},buildConfigurationList=ident(60),compatibilityVersion="Xcode 14.0",developmentRegion="en",knownRegions=["en","Base"],mainGroup=ident(40),productRefGroup=ident(41),projectDirPath="",projectRoot="",targets=[ident(70),ident(71)])
(PROJECT/"project.pbxproj").write_bytes(plistlib.dumps(dict(archiveVersion="1",classes={},objectVersion="56",objects=objects,rootObject=ident(1))))
scheme = PROJECT/"xcshareddata/xcschemes";scheme.mkdir(parents=True,exist_ok=True)
(WORK/"TransferFilesCheck.xctestplan").write_text(json.dumps({"configurations":[{"id":"B6AA15B3-3806-4972-B11D-24C5D3A2E993","name":"Text diagnostics","options":{}}],"defaultOptions":{"uiTestingScreenshotsLifetime":"keepNever","userAttachmentLifetime":"keepNever","preferredScreenCaptureFormat":"screenshots"},"testTargets":[{"target":{"containerPath":"container:TransferFilesCheck.xcodeproj","identifier":ident(71),"name":"TransferFilesCheckUITests"}}],"version":1},indent=2))
def ref(target,name):return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ident(target)}" BuildableName="{name}" BlueprintName="{name.split(".")[0]}" ReferencedContainer="container:TransferFilesCheck.xcodeproj"/>'
(scheme/"TransferFilesCheck.xcscheme").write_text(f'''<?xml version="1.0" encoding="UTF-8"?><Scheme LastUpgradeVersion="1620" version="1.3"><BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES">{ref(70,"TransferFilesCheck.app")}</BuildActionEntry><BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES">{ref(71,"TransferFilesCheckUITests.xctest")}</BuildActionEntry></BuildActionEntries></BuildAction><TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><TestPlans><TestPlanReference reference="container:TransferFilesCheck.xctestplan" default="YES"/></TestPlans></TestAction><LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="NO"><BuildableProductRunnable runnableDebuggingMode="0">{ref(70,"TransferFilesCheck.app")}</BuildableProductRunnable></LaunchAction></Scheme>''')
print(PROJECT)
