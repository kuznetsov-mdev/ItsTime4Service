from pathlib import Path
import argparse,hashlib,json,re,subprocess,time,uuid,xml.etree.ElementTree as E

ROOT=Path(__file__).resolve().parents[2]
PKG='ru.kuznetsov.itstime4service.transfercheck'
ADB=['adb','-s','emulator-5554']
RUN_FOLDER='Run'+uuid.uuid4().hex[:8]
ALLOWED={RUN_FOLDER,'New folder','OK','Create','Plain','Protected','Save','SAVE','Pick','Share','Ready','Ready Plain','Ready Protected','Working','Saved','Shared','Cancelled','CoroutineCancelled','Cancel','CANCEL','Downloads','Download','TransferFilesCheck','Transfer Sink','Plain.it4s','Protected.it4s','Show roots','Open navigation drawer','More options'}
expected={name.lower():hashlib.sha256((ROOT/'androidApp/src/transferCheck/assets'/f'{name}.it4s').read_bytes()).hexdigest() for name in ['Plain','Protected']}
parser=argparse.ArgumentParser();parser.add_argument('--step',choices=['inspect','all'],default='all');args=parser.parse_args()

def adb(*args):
    r=subprocess.run(ADB+list(args),capture_output=True,text=True,timeout=30)
    if r.returncode:raise RuntimeError('ADB command failed: '+args[0])
    return r.stdout

def nodes():
    dumped=adb('shell','uiautomator','dump','/data/local/tmp/it4s-transfer-ui.xml')
    if 'dumped to:' not in dumped:return []
    data=adb('shell','cat','/data/local/tmp/it4s-transfer-ui.xml')
    return [n.attrib for n in E.fromstring(data).iter('node') if n.get('enabled')=='true']

def redacted(items):
    return [{'text':n.get('text') if n.get('text','').casefold() in {s.casefold() for s in ALLOWED} or n.get('text','').startswith('Read ') else '<redacted>',
             'desc':n.get('content-desc') if n.get('content-desc') in ALLOWED else '',
             'id':n.get('resource-id',''),'bounds':n.get('bounds','')} for n in items if n.get('package')==PKG or 'documentsui' in n.get('package','') or n.get('package') in {'android','com.android.intentresolver'}]

def wait(predicate,timeout=40):
    end=time.monotonic()+timeout;last=[]
    while time.monotonic()<end:
        last=nodes()
        found=[n for n in last if predicate(n)]
        if found:return found[0]
        time.sleep(.25)
    path=Path('/private/tmp/it4s-files-android-redacted.json');path.write_text(json.dumps(redacted(last),ensure_ascii=False,indent=2))
    raise RuntimeError('UI selector timed out; redacted diagnostics: '+str(path))

def tap_node(n):
    left,top,right,bottom=map(int,re.findall(r'\d+',n['bounds']))
    adb('shell','input','tap',str((left+right)//2),str((top+bottom)//2))

def tap_text(label):tap_node(wait(lambda n:n.get('text','').casefold()==label.casefold()))
def status(value):wait(lambda n:n.get('package')==PKG and n.get('text')==value)
def documents():wait(lambda n:'documentsui' in n.get('package',''))

def folder():
    items=nodes()
    roots=next((n for n in items if n.get('content-desc') in {'Show roots','Open navigation drawer'}),None)
    if roots:
        tap_node(roots)
        tap_text('Downloads')
    current=nodes()
    own=next((n for n in current if n.get('text')=='TransferFilesCheck'),None)
    if own:tap_node(own)
    else:tap_text('TransferFilesCheck')
    current=nodes()
    if not any(n.get('text')==RUN_FOLDER for n in current):
        create=next((n for n in current if n.get('content-desc') in {'New folder','Create new folder'} or n.get('resource-id','').endswith('/menu_create_dir')),None)
        if create:tap_node(create)
        else:
            tap_node(wait(lambda n:n.get('content-desc')=='More options'))
            tap_text('New folder')
        field=wait(lambda n:n.get('class')=='android.widget.EditText')
        tap_node(field);adb('shell','input','text',RUN_FOLDER)
        tap_node(wait(lambda n:n.get('text') in {'OK','Create','CREATE'}))
    tap_text(RUN_FOLDER)

def save(kind):
    tap_text(kind);tap_text('Save');documents();folder()
    tap_text('Save');status('Saved')
    adb('pull',f'/sdcard/Download/TransferFilesCheck/{RUN_FOLDER}/{kind}.it4s',f'/private/tmp/it4s-android-{kind}.it4s')
    actual=hashlib.sha256(Path(f'/private/tmp/it4s-android-{kind}.it4s').read_bytes()).hexdigest()
    assert actual==expected[kind.lower()],'Saved file hash differs'
    tap_text('Pick');documents();folder();tap_text(kind+'.it4s');status('Read '+expected[kind.lower()])
    print(kind+' system save/open bytes PASS',flush=True)

adb('shell','input','keyevent','224')
adb('shell','wm','dismiss-keyguard')
adb('shell','am','force-stop',PKG)
adb('shell','am','start','-W','-f','0x10008000','-n',PKG+'/ru.kuznetsov.itstime4service.TransferFilesCheckActivity')
wait(lambda n:n.get('package')==PKG and n.get('text','').casefold()=='pick')
if args.step=='inspect':
    tap_text('Pick');documents();print(json.dumps(redacted(nodes()),ensure_ascii=False));raise SystemExit(0)
adb('shell','mkdir','-p','/sdcard/Download/TransferFilesCheck')
for label in ['Pick','Save','Share']:
    tap_text(label)
    if label!='Share':documents()
    else:wait(lambda n:n.get('package') in {'android','com.android.intentresolver'} and n.get('text') not in {'','Working'})
    for attempt in range(8):
        current=nodes()
        if any(n.get('package')==PKG and n.get('text')=='Cancelled' for n in current):break
        if any('documentsui' in n.get('package','') or n.get('package') in {'android','com.android.intentresolver'} for n in current):
            adb('shell','input','keyevent','4')
        time.sleep(.3)
    status('Cancelled');print(label+' cancellation PASS',flush=True)
save('Plain');save('Protected')
tap_text('Share');tap_text('Transfer Sink');status('Shared')
received=adb('shell','run-as',PKG,'cat','files/received-sha.txt').strip()
assert received==expected['protected'],'Share recipient hash differs'
print('Protected chosen-target handoff/hash PASS',flush=True)
Path('/private/tmp/it4s-files-android-result.json').write_text(json.dumps({'cases':6,'status':'PASS','plainSha256':expected['plain'],'protectedSha256':expected['protected']}))
