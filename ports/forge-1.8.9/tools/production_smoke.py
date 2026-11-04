"""Launch the exact SRG-remapped Forge artifact with pristine 1.8.9 and Java 8."""
import hashlib,json,os,shutil,subprocess,time,uuid,zipfile,urllib.request
from pathlib import Path

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def main():
    root=Path(__file__).resolve().parents[1];cache=Path.home()/'.gradle/caches';loom=cache/'essential-loom/1.8.9'
    jar=root/'build/libs/curryfarts-5.0-port.3+1.8.9-forge.jar';qa=root/'build/libs/curryfarts-5.0-port.3+1.8.9-qa.jar';java=next((root/'.tools/java8').glob('*/bin/java.exe'))
    run=root/'build/qa'/time.strftime('%Y%m%d-%H%M%S');(run/'mods').mkdir(parents=True);(root/'.tools/latest-qa-run.txt').write_text(str(run))
    for file in [jar,qa]:shutil.copy2(file,run/'mods'/file.name)
    runtime=[Path(p) for p in (root/'.tools/runtime-classpath.txt').read_text().splitlines() if 'essential-loom' not in p and 'fabric-loom' not in p and not any(x in p for x in ['-natives-linux','-natives-osx','-natives-windows-32'])]
    runtime=[p for p in runtime if 'log4j-' not in p.name]
    for library in json.loads((loom/'minecraft-info.json').read_bytes())['libraries']:
        if 'org.apache.logging.log4j:' in library['name']:
            d=library['downloads']['artifact'];target=root/'.tools/production-runtime'/Path(d['path']).name;target.parent.mkdir(exist_ok=True)
            if not target.is_file():target.write_bytes(urllib.request.urlopen(urllib.request.Request(d['url'],headers={'User-Agent':'Mozilla/5.0'})).read())
            assert hashlib.sha1(target.read_bytes()).hexdigest()==d['sha1'];runtime.append(target)
    runtime.append(loom/'minecraft-client.jar');forge=next((cache/'modules-2/files-2.1/net.minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9').glob('*/*-universal.jar'));runtime.append(forge)
    natives=run/'natives';natives.mkdir()
    for path in runtime:
        if 'natives-windows' in path.name:
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    if name.endswith('.dll'): (natives/Path(name).name).write_bytes(archive.read(name))
    assets=cache/'essential-loom/assets';index=next((assets/'indexes').glob('1.8.9-*.json'))
    (run/'options.txt').write_text('guiScale:0\nfullscreen:false\nmaxFps:60\nrenderDistance:4\n')
    fixture=root.parents[2]/'Autism-Client/.tools/crypto-qa/legacy.bin'
    if fixture.is_file():shutil.copy2(fixture,run/'legacy-fixture.bin')
    args=[str(java),'-Xmx2G','-Djava.library.path='+str(natives),'-Dlog4j2.formatMsgNoLookups=true','-Dcurryfarts.qa.result='+str(run/'result.json'),'-Dcurryfarts.qa.sha256='+sha(jar),'-cp',os.pathsep.join(map(str,runtime)),'net.minecraft.launchwrapper.Launch','--username','CurryQa','--uuid',uuid.uuid3(uuid.NAMESPACE_DNS,'OfflinePlayer:CurryQa').hex,'--accessToken','0','--userType','legacy','--userProperties','{}','--version','1.8.9','--gameDir',str(run),'--assetsDir',str(assets),'--assetIndex',index.stem,'--width','1280','--height','720','--tweakClass','net.minecraftforge.fml.common.launcher.FMLTweaker']
    (run/'launch-plan.json').write_text(json.dumps({'minecraft':'1.8.9','forge':'11.15.1.2318','jarSha256':sha(jar),'runtime':[{'path':str(p),'sha256':sha(p)} for p in runtime]},indent=2))
    print('Packaged Forge QA:',run,flush=True)
    with (run/'launcher.log').open('w') as log:
        process=subprocess.Popen(args,cwd=run,stdout=log,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        try:code=process.wait(timeout=210)
        except subprocess.TimeoutExpired:process.kill();process.wait();raise RuntimeError('Client timeout: '+str(run))
    result=json.loads((run/'result.json').read_text()) if (run/'result.json').is_file() else {'status':'NO_RESULT'}
    verdict={'passed':code==0 and result.get('status')=='PASSED','exitCode':code,'minecraft':'1.8.9','java':'8u504','forge':'11.15.1.2318','jarSha256':sha(jar),'result':result};(run/'verdict.json').write_text(json.dumps(verdict,indent=2));print(json.dumps(verdict,indent=2))
    if not verdict['passed']:raise RuntimeError('Packaged client failed: '+str(run/'launcher.log'))
if __name__=='__main__':main()
