"""Check synthetic upstream NBT/DPAPI and Meteor compatibility using Java 8."""
import argparse,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--project',type=Path,required=True);args=parser.parse_args();project=args.project.resolve()
    java_home=Path(os.environ['JAVA8_HOME']) if 'JAVA8_HOME' in os.environ else next((project/'.tools/java8').glob('*'))
    java=java_home/'bin/java.exe'
    if not java.is_file():java=java_home/'bin/java'
    classpath=(project/'.tools/qa-classpath.txt').read_text().strip()
    fixture=ROOT/'.tools/compat-fixture'
    if not (fixture/'expected.json').is_file():raise SystemExit('Create the synthetic fixture with tools/qa/create_compat_fixture.py first')
    run=project/'build/core-compat'
    # CoreCompatibility uses deterministic synthetic IDs; use a new child per invocation.
    import uuid
    run=run/uuid.uuid4().hex[:10]
    subprocess.run([str(java),'-cp',classpath,'curryfarts.qa.CoreCompatibility',str(fixture),str(run)],cwd=project,check=True)
if __name__=='__main__':main()
