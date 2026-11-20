from pathlib import Path
import argparse,json,re,subprocess,time,uuid,fcntl,os,signal,threading

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser()
parser.add_argument("--only",choices=["testNativeSaveControl","testCancelPick","testCancelSave","testCancelShare","testSaveAndReadPlain","testSaveAndReadProtected","testShareToFilesHasConfirmedCompletion"])
parser.add_argument("--device",required=True)
parser.add_argument("--skip-prepare",action="store_true")
args=parser.parse_args()
work=Path("/private/tmp/it4s-transfer-ios-check")
work.mkdir(parents=True,exist_ok=True)
lock=(work/"runner.lock").open("a")
try:
    fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit("Another native file check owns the simulator workflow")
try:
    subprocess.run(["xcrun","simctl","bootstatus",args.device,"-b"],check=True,capture_output=True,text=True,timeout=180)
    subprocess.run(["open","-a","/Applications/Xcode.app/Contents/Applications/DeviceHub.app","--args","-CurrentDeviceUDID",args.device],check=True,capture_output=True,text=True,timeout=30)
except (subprocess.CalledProcessError,subprocess.TimeoutExpired) as error:
    print(json.dumps({"exit_status":2,"preflight":"simulator boot or GUI attachment failed","error_type":type(error).__name__}))
    raise SystemExit(2)
print(json.dumps({"preflight":"booted; GUI requested","device":args.device}),flush=True)
if not args.skip_prepare:
    subprocess.run(["python3",str(ROOT/"tools/transfer-files-check/prepare_ios.py")],check=True,capture_output=True,text=True)
stamp=time.strftime("%Y%m%d-%H%M%S")+"-"+uuid.uuid4().hex[:6]
result=work/("result-"+stamp+".xcresult")
log=work/("build-"+stamp+".log")
command=["xcodebuild","-project",str(work/"TransferFilesCheck.xcodeproj"),"-scheme","TransferFilesCheck","-destination","platform=iOS Simulator,id="+args.device,"-derivedDataPath",str(work/("DerivedData-"+args.device)),"-resultBundlePath",str(result),"-destination-timeout","60","-parallel-testing-enabled","NO","-test-timeouts-enabled","YES","-default-test-execution-time-allowance","180","-maximum-test-execution-time-allowance","240","-collect-test-diagnostics","never","-disableAutomaticPackageResolution","CODE_SIGNING_ALLOWED=YES","CODE_SIGN_IDENTITY=-","test"]
if args.only:command.append("-only-testing:TransferFilesCheckUITests/TransferFilesCheckUITests/"+args.only)
errors=[];tests=[];diagnostics=[]
with log.open("w") as output:
    child=subprocess.Popen(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,start_new_session=True)
    timed_out=threading.Event()
    def stop_owned_run():
        timed_out.set()
        if child.poll() is None:
            os.killpg(child.pid,signal.SIGINT)
    timeout_seconds=360 if args.only else 1200
    watchdog=threading.Timer(timeout_seconds,stop_owned_run)
    watchdog.start()
    for line in child.stdout:
        output.write(line)
        output.flush()
        if re.search(r"(?:^|\s)error:",line) and len(errors)<10:
            errors.append(re.sub(r"/Users/[^/]+", "<home>",line.strip())[:500])
        if line.startswith("FILECHECK_DIAGNOSTIC") and len(diagnostics)<10:diagnostics.append(line.strip()[:500])
        if line.startswith("Test Case") and "started" in line:
            print(json.dumps({"started":line.strip()[:250]}),flush=True)
        if line.startswith("Test Case") and ("passed" in line or "failed" in line):
            tests.append(line.strip()[:300])
            print(json.dumps({"test":tests[-1]}),flush=True)
    code=child.wait()
    watchdog.cancel()
    if timed_out.is_set():
        errors.append("Native test workflow exceeded "+str(timeout_seconds)+" seconds")
        if code==0:code=5
summary={}
screen_attachments=0
if result.exists():
    read=subprocess.run(["xcrun","xcresulttool","get","test-results","summary","--path",str(result)],capture_output=True,text=True)
    if read.returncode==0:
        data=json.loads(read.stdout)
        summary={k:data[k] for k in ["totalTestCount","passedTests","failedTests","skippedTests"] if k in data}
    def objects(value):
        if isinstance(value,dict):
            yield value
            for child in value.values():yield from objects(child)
        elif isinstance(value,list):
            for child in value:yield from objects(child)
    tree=subprocess.run(["xcrun","xcresulttool","get","test-results","tests","--path",str(result),"--compact"],capture_output=True,text=True)
    if tree.returncode==0:
        cases=[node["nodeIdentifier"] for node in objects(json.loads(tree.stdout)) if node.get("nodeType")=="Test Case"]
        for case in cases:
            activities=subprocess.run(["xcrun","xcresulttool","get","test-results","activities","--path",str(result),"--test-id",case,"--compact"],capture_output=True,text=True)
            if activities.returncode!=0:
                if code==0:code=4
                errors.append("Cannot verify screen attachment policy")
                break
            for node in objects(json.loads(activities.stdout)):
                for attachment in node.get("attachments",[]):
                    if any(term in attachment.get("name", "").lower() for term in ["screenshot","screen recording","ui snapshot"]):screen_attachments+=1
        if screen_attachments:
            if code==0:code=4
            errors.append("Unexpected screen attachments retained")
    else:
        if code==0:code=4
        errors.append("Cannot verify screen attachment policy")
if code == 0 and summary.get("totalTestCount",0) == 0:
    code=3
    errors.append("No requested UI tests executed")
print(json.dumps(dict(exit_status=code,errors=errors,diagnostics=diagnostics,tests=tests,summary=summary,screen_attachments=screen_attachments,result=str(result),log=str(log))))
raise SystemExit(code)
