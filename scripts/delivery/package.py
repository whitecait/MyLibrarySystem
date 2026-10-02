#!/usr/bin/env python3
"""Build a local delivery archive from an exact clean Git commit and pinned base images."""
import argparse
import gzip
import io
import json
from pathlib import Path
import shutil
import subprocess
import tarfile
from manage import digest,POSTGRES,invoke

ROOT=Path(__file__).resolve().parents[2]

def build(output,commit):
    head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    requested=subprocess.check_output(['git','rev-parse',commit],cwd=ROOT,text=True).strip()
    if head!=requested or subprocess.check_output(['git','status','--porcelain'],cwd=ROOT):
        raise ValueError('Packaging requires the requested commit checked out with a clean tracked/untracked worktree')
    output=output.resolve();output.mkdir(parents=True,exist_ok=True)
    package=output/('MyLibrarySystem-'+head[:12])
    package.mkdir()  # Refuse replacing an existing package.
    source=package/'source';source.mkdir()
    archive=subprocess.check_output(['git','archive','--format=tar',head],cwd=ROOT)
    with tarfile.open(fileobj=io.BytesIO(archive)) as stream:stream.extractall(source,filter='data')
    bin_dir=package/'bin';bin_dir.mkdir();config=package/'config';config.mkdir()
    for name in ['manage.py','windows.ps1']:shutil.copyfile(source/'scripts/delivery'/name,bin_dir/name)
    backend='mls-backend:release-'+head[:12];frontend='mls-frontend:release-'+head[:12]
    for directory,tag in [('library-server',backend),('library-frontend',frontend)]:
        invoke(['docker','build','--label','org.opencontainers.image.revision='+head,'-t',tag,str(source/directory)])
    invoke(['docker','pull',POSTGRES])
    images=[]
    for tag in [backend,frontend,POSTGRES]:
        identity=invoke(['docker','image','inspect','--format','{{.Id}}',tag],capture_output=True,text=True).stdout.strip()
        images.append({'tag':tag,'id':identity,'source_commit':head if tag!=POSTGRES else None})
    (package/'images.json').write_text(json.dumps(images,indent=2)+'\n')
    image_tar=package/'images.tar.gz'
    with image_tar.open('xb') as raw:
        with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as zipped:
            process=subprocess.Popen(['docker','save',backend,frontend,POSTGRES],stdout=subprocess.PIPE)
            shutil.copyfileobj(process.stdout,zipped)
            if process.wait()!=0:raise RuntimeError('Image export failed')
    template=(source/'.env.example').read_text()
    # Ship a dedicated template, not a local private .env.
    template='\n'.join(line for line in template.splitlines() if not line.startswith(('BACKEND_IMAGE=','FRONTEND_IMAGE=')))+'\n'
    import re
    template=re.sub(r'^DB_PASSWORD=.*$','DB_PASSWORD=REPLACE_WITH_RANDOM_VALUE',template,flags=re.M)
    template=re.sub(r'^AI_VISITOR_COOKIE_SIGNING_KEY=.*$','AI_VISITOR_COOKIE_SIGNING_KEY=REPLACE_WITH_64_HEX_CHARACTERS',template,flags=re.M)
    for key in ['AI_ENDPOINT_URL','AI_MODEL','AI_API_KEY']:
        template=re.sub(r'^'+key+r'=.*$',key+'=',template,flags=re.M)
    template+='BACKEND_IMAGE='+backend+'\nFRONTEND_IMAGE='+frontend+'\n'
    (config/'runtime.env.example').write_text(template)
    (config/'compose.images.yaml').write_text('services:\n  database:\n    image: '+POSTGRES+'\n')
    instructions=(source/'scripts/delivery/README.md').read_text()
    instructions=instructions.replace('../../docs/','source/docs/').replace('../../formal/','source/formal/').replace('../../ENGINEERING.md','source/ENGINEERING.md').replace('(manage.py)','(bin/manage.py)').replace('(package.py)','(source/scripts/delivery/package.py)')
    (package/'README.md').write_text(instructions)
    (package/'release.json').write_text(json.dumps({'commit':head,'images':images,'ci':'Verify PR and merged-commit CI separately; package creation is not acceptance.','data':'Dedicated learning data; loopback only.'},indent=2)+'\n')
    files=sorted(p for p in package.rglob('*') if p.is_file())
    (package/'SHA256SUMS').write_text(''.join(digest(path)+'  '+str(path.relative_to(package)).replace('\\','/')+'\n' for path in files))
    target=output/(package.name+'.tar.gz')
    with target.open('xb') as raw:
        with tarfile.open(fileobj=raw,mode='w:gz') as stream:stream.add(package,arcname=package.name)
    (output/(package.name+'.seal.json')).write_text(json.dumps({'commit':head,'archive':str(target),'sha256':digest(target),'bytes':target.stat().st_size},indent=2)+'\n')
    print('Package sealed:',target)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output-dir',type=Path,required=True);p.add_argument('--commit',required=True)
    args=p.parse_args();build(args.output_dir,args.commit)
if __name__=='__main__':main()
