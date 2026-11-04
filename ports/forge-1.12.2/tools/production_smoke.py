"""Run the exact reobfuscated Forge JAR in a fresh synthetic game directory."""
import hashlib,json,os,subprocess,time,uuid
from pathlib import Path


def main():
    root=Path(__file__).resolve().parents[1]
    run=root/'build/qa'/(time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:6])
    run.mkdir(parents=True)
    (root/'.tools').mkdir(exist_ok=True)
    (root/'.tools/latest-qa-run.txt').write_text(str(run))
    (run/'options.txt').write_text('guiScale:0\nfullscreen:false\nmaxFps:60\nrenderDistance:4\n')
    command=[str(root/'gradlew.bat'),'--console=plain','runObfClient','-PqaRunDir='+str(run)]
    with (run/'launcher.log').open('w') as log:
        process=subprocess.Popen(command,cwd=root,stdout=log,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        try:code=process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True)
            raise RuntimeError('QA timeout: '+str(run))
    result=json.loads((run/'qa-result.json').read_text()) if (run/'qa-result.json').is_file() else {'status':'NO_RESULT'}
    jar=root/'build/libs/curryfarts-5.0-port.3+1.12.2-forge.jar'
    digest=hashlib.sha256(jar.read_bytes()).hexdigest()
    verdict={'passed':code==0 and result.get('status')=='PASSED' and result.get('jarSha256')==digest,'exitCode':code,'sourceSha256':digest,'minecraft':'1.12.2','forge':'14.23.5.2847','java':'8u504','result':result}
    (run/'verdict.json').write_text(json.dumps(verdict,indent=2)+'\n')
    print(json.dumps(verdict,indent=2))
    if not verdict['passed']:raise RuntimeError('Packaged client failed: '+str(run/'launcher.log'))


if __name__=='__main__':main()
