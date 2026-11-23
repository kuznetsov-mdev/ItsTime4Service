from pathlib import Path
import argparse,json,re,sqlite3,subprocess,time,xml.etree.ElementTree as E

parser=argparse.ArgumentParser()
parser.add_argument('--metadata',type=Path,required=True)
parser.add_argument('--stage',choices=['seed','verify'],required=True)
args=parser.parse_args()
meta=json.loads(args.metadata.read_text())
PKG=meta['package']
if not re.fullmatch(r'ru\.kuznetsov\.itstime4service\.upgradecheck[0-9a-f]+',PKG):
    raise SystemExit('Only an isolated upgrade-check package is supported')
ADB=['adb','-s','emulator-5554']
work=args.metadata.parent
CAR='SyntheticUpgradeCar'
ALLOW={CAR,'SyntheticGarage','SyntheticUpgradeComment','Add car','Save car','Save','Settings','Dark','Miles','Mark as done','Back','Import and export'}

def adb(*parts,binary=False):
    result=subprocess.run(ADB+list(parts),capture_output=True,text=not binary,timeout=60)
    if result.returncode:raise RuntimeError('ADB action failed: '+parts[0])
    return result.stdout

def nodes():
    result=adb('shell','uiautomator','dump','/data/local/tmp/it4s-upgrade-ui.xml')
    if 'dumped to:' not in result:return []
    try:return [n.attrib for n in E.fromstring(adb('shell','cat','/data/local/tmp/it4s-upgrade-ui.xml')).iter('node')]
    except E.ParseError:return []

def wait(predicate,timeout=35):
    end=time.monotonic()+timeout
    last=[]
    while time.monotonic()<end:
        last=nodes()
        deny=next((n for n in last if n.get('resource-id','').endswith('/permission_deny_button')),None)
        if deny:tap(deny);continue
        found=next((n for n in last if predicate(n)),None)
        if found:return found
        time.sleep(.3)
    safe=[{k:(n.get(k,'') if k not in ['text','content-desc'] or n.get(k,'') in ALLOW else '<redacted>') for k in ['text','content-desc','class','bounds']} for n in last if n.get('package')==PKG]
    (work/'ui-redacted.json').write_text(json.dumps(safe,indent=2))
    raise RuntimeError('UI selector timed out; redacted diagnostics retained')

def tap(node):
    left,top,right,bottom=map(int,re.findall(r'\d+',node['bounds']))
    adb('shell','input','tap',str((left+right)//2),str((top+bottom)//2))

def label(text,timeout=35):return wait(lambda n:text in [n.get('text'),n.get('content-desc')],timeout)
def click(text):tap(label(text))
def scroll_click(text):
    for attempt in range(8):
        found=next((n for n in nodes() if text in [n.get('text'),n.get('content-desc')]),None)
        if found:tap(found);return
        adb('shell','input','swipe','500','1450','500','450','350')
    raise RuntimeError('Scrollable action not found: '+text)

def edit(index,value):
    deadline=time.monotonic()+35
    while True:
        fields=[n for n in nodes() if n.get('package')==PKG and n.get('class')=='android.widget.EditText']
        if len(fields)>index:break
        if time.monotonic()>=deadline:raise RuntimeError('Expected input is not visible')
        time.sleep(.3)
    tap(fields[index])
    time.sleep(.3)
    for char in value:
        adb('shell','input','text',char);time.sleep(.08)

def launch():
    adb('shell','input','keyevent','224');adb('shell','wm','dismiss-keyguard')
    adb('shell','am','force-stop',PKG)
    adb('shell','am','start','-W','-n',PKG+'/ru.kuznetsov.itstime4service.MainActivity')

def version():
    package=adb('shell','dumpsys','package',PKG)
    found=re.search(r'versionName=([^\s]+)',package)
    return found.group(1) if found else None

def capture(stage):
    adb('shell','am','force-stop',PKG)
    directory=work/stage
    directory.mkdir(exist_ok=True)
    names=adb('shell','run-as',PKG,'ls','databases').split()
    for name in names:
        if name not in ['itstime4service.db','itstime4service.db-wal','itstime4service.db-shm','itstime4service.db-journal']:continue
        (directory/name).write_bytes(adb('exec-out','run-as',PKG,'cat','databases/'+name,binary=True))
    db=sqlite3.connect('file:'+str(directory/'itstime4service.db')+'?mode=ro',uri=True)
    result={'schema':db.execute('PRAGMA user_version').fetchone()[0],'tables':{}}
    for table in ['cars','maintenance_tasks','service_history','settings']:
        columns=[row[1] for row in db.execute('PRAGMA table_info('+table+')')]
        rows=db.execute('SELECT * FROM '+table+' ORDER BY rowid').fetchall()
        result['tables'][table]={'columns':columns,'rows':rows}
    assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    db.close()
    (work/(stage+'.json')).write_text(json.dumps(result,indent=2))
    return result

if args.stage=='seed':
    if (work/'before.json').exists():raise SystemExit('Baseline already recorded; use verify after the update')
    if version() is None:
        adb('install','-r',meta['old_apk'])
        launch()
        label('Add car')
    assert version()=='1.0.0','Unexpected baseline version'
    previous=capture('checkpoint')
    existing=previous['tables']['cars']
    if existing['rows']:
        assert len(existing['rows'])==1
        CAR=existing['rows'][0][existing['columns'].index('brand')]
        ALLOW.add(CAR)
        launch();label(CAR)
    else:
        launch();click('Add car')
        edit(0,CAR);edit(1,'12000');edit(2,'TESTVIN0000000001')
        scroll_click('Save car');label(CAR)
    if not any(n.get('content-desc')=='Mark as done' for n in nodes()):click(CAR)
    click('Mark as done')
    edit(0,'SyntheticGarage');edit(1,'5000');edit(2,'SyntheticUpgradeComment')
    scroll_click('Save');label(CAR)
    click('Back');click('Settings');click('Dark');click('Miles')
    snapshot=capture('before')
    counts={name:len(value['rows']) for name,value in snapshot['tables'].items()}
    assert counts=={'cars':1,'maintenance_tasks':1,'service_history':1,'settings':1},counts
    print(json.dumps({'stage':'baseline seeded','version':'1.0.0','schema':snapshot['schema'],'rows':counts}),flush=True)
else:
    before=json.loads((work/'before.json').read_text())
    old_car=before['tables']['cars']
    CAR=old_car['rows'][0][old_car['columns'].index('brand')]
    ALLOW.add(CAR)
    adb('install','-r',meta['new_apk'])
    assert version()=='1.1.0','Unexpected updated version'
    launch();label(CAR)
    if not any(n.get('text')=='Settings' for n in nodes()):click('Back')
    click('Settings');scroll_click('Import and export')
    label('Import from file')
    after=capture('after')
    for table,old in before['tables'].items():
        new=after['tables'][table]
        projection=[new['columns'].index(name) for name in old['columns']]
        rows=[[row[index] for index in projection] for row in new['rows']]
        assert rows==old['rows'],'Stored data changed: '+table
    assert after['schema']>before['schema']
    result={'status':'PASS','from':'1.0.0','to':'1.1.0','schemaBefore':before['schema'],'schemaAfter':after['schema'],
        'rows':{name:len(value['rows']) for name,value in after['tables'].items()},'integrityCheck':'ok','legacyColumnsPreserved':True}
    (work/'result.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result),flush=True)
