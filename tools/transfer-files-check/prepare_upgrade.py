from pathlib import Path
import io,json,os,subprocess,tarfile,time,uuid

ROOT=Path(__file__).resolve().parents[2]
BASELINE='00fd4d2'
stamp=time.strftime('%Y%m%d%H%M%S')+uuid.uuid4().hex[:4]
work=Path('/private/tmp')/('it4s-upgrade-'+stamp)
old=work/'baseline'
old.mkdir(parents=True)
archive=subprocess.run(['git','archive','--format=tar',BASELINE],cwd=ROOT,check=True,capture_output=True).stdout
with tarfile.open(fileobj=io.BytesIO(archive),mode='r:') as source:
    source.extractall(old,filter='data')
os.symlink(ROOT/'local.properties',old/'local.properties')
suffix='.upgradecheck'+stamp
init=work/'upgrade.init.gradle'
init.write_text('''gradle.beforeProject { project ->
    project.pluginManager.withPlugin('com.android.application') {
        project.extensions.getByName('androidComponents').finalizeDsl { android ->
            def type = android.buildTypes.create('upgradeCheck')
            type.initWith(android.buildTypes.getByName('debug'))
            type.applicationIdSuffix = 'SUFFIX'
            type.matchingFallbacks.add('debug')
        }
    }
}
'''.replace('SUFFIX',suffix))
metadata=dict(baseline=BASELINE,baseline_root=str(old),init=str(init),package='ru.kuznetsov.itstime4service'+suffix,
    old_apk=str(old/'androidApp/build/outputs/apk/upgradeCheck/androidApp-upgradeCheck.apk'),
    new_apk=str(ROOT/'androidApp/build/outputs/apk/upgradeCheck/androidApp-upgradeCheck.apk'))
(work/'check.json').write_text(json.dumps(metadata,indent=2))
print(json.dumps(dict(metadata=str(work/'check.json'),**metadata)))
