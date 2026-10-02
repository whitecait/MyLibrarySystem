#!/usr/bin/env python3
"""Portable Docker delivery operations; native Windows entry: windows.ps1."""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys

POSTGRES='postgres:17.6-bookworm@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3'

def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def verify(root):
    for line in (root/'SHA256SUMS').read_text().splitlines():
        wanted,name=line.split('  ',1)
        path=(root/name).resolve()
        if not path.is_relative_to(root.resolve()) or not path.is_file() or digest(path)!=wanted:
            raise ValueError('Package checksum failed: '+name)
    print('Package checksums verified.')

def project_name(name):
    if not re.fullmatch(r'[a-z0-9][a-z0-9_-]{0,62}',name):raise ValueError('Invalid Compose project name')
    return name

def invoke(command,**kwargs):
    result=subprocess.run(command,**kwargs)
    if result.returncode:raise RuntimeError('Operation failed with exit code '+str(result.returncode))
    return result

def compose(root,env,project,*args,**kwargs):
    if not env.is_file():raise ValueError('Private environment file is missing; run init first')
    child_env = dict(os.environ)
    # Explicit private-file inputs take precedence over unrelated shell exports.
    for line in env.read_text(encoding='utf-8').splitlines():
        if line.startswith('#') or '=' not in line: continue
        key = line.split('=', 1)[0].strip()
        if re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', key): child_env.pop(key, None)
    return invoke(['docker','compose','--env-file',str(env),'-p',project_name(project),
                   '-f',str(root/'source/compose.yaml'),'-f',str(root/'config/compose.images.yaml'),*args],env=child_env,**kwargs)

def init(root,env,port):
    if not 1024<=port<=65535:raise ValueError('Port must be between 1024 and 65535')
    template=(root/'config/runtime.env.example').read_text()
    text=template.replace('REPLACE_WITH_RANDOM_VALUE',secrets.token_hex(24)).replace('REPLACE_WITH_64_HEX_CHARACTERS',secrets.token_hex(32))
    text=re.sub(r'^FRONTEND_PORT=.*$',f'FRONTEND_PORT={port}',text,flags=re.M)
    env.parent.mkdir(parents=True,exist_ok=True)
    fd=os.open(env,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w',encoding='utf-8',newline='\n') as stream:stream.write(text)
    print('Private configuration created. Set model endpoint, model and key locally before AI checks.')

def restart(root,env,project):
    # Dependency order prevents frontend DNS failures during concurrent restarts.
    compose(root,env,project,'stop','frontend','backend','database')
    compose(root,env,project,'up','-d','--no-build','--wait','--wait-timeout','180')

def backup(root,env,project,path):
    # Create exclusively before stopping the application; binary stdout preserves SQL bytes on Windows.
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'wb') as stream:
        try:
            compose(root,env,project,'stop','frontend','backend')
            compose(root,env,project,'exec','-T','database','pg_dump','-U','library','-d','library',stdout=stream)
        except Exception:
            print('Backup failed; output is incomplete and source may remain stopped.',file=sys.stderr)
            raise
    if path.stat().st_size==0:raise ValueError('Backup is empty; source application remains stopped')
    print('Backup completed; source application remains stopped. Run start to resume it.')

def restore(root,source_env,source_project,path,target_env,target_project):
    project_name(target_project)
    if target_project==source_project:raise ValueError('Restore project must differ from source')
    if target_env.resolve()==source_env.resolve():raise ValueError('Restore requires a separate target environment')
    if not path.is_file() or not target_env.is_file():raise ValueError('Backup and target environment files must exist')
    if path.stat().st_size == 0:raise ValueError('Empty backup cannot be restored')
    for command in [ ['docker','ps','-aq','--filter',f'label=com.docker.compose.project={target_project}'],
                     ['docker','volume','ls','-q','--filter',f'label=com.docker.compose.project={target_project}'] ]:
        if invoke(command,capture_output=True,text=True).stdout.strip():
            raise ValueError('Restore target already has containers or volumes; use a new project')
    compose(root,target_env,target_project,'up','-d','--no-build','--wait','--wait-timeout','180','database')
    with path.open('rb') as stream:
        compose(root,target_env,target_project,'exec','-T','database','psql','-U','library','-d','library','-v','ON_ERROR_STOP=1',stdin=stream)
    compose(root,target_env,target_project,'up','-d','--no-build','--wait','--wait-timeout','180','backend','frontend')
    print('New-project restoration finished; verify data and core writes before accepting it.')

def bootstrap(root,env,project,username,role):
    if not username or len(username)>64:raise ValueError('Username must contain 1–64 characters')
    password=getpass.getpass('New staff password: ')
    if len(password)<8 or password!=getpass.getpass('Repeat staff password: '):
        raise ValueError('Password must have at least 8 characters and match')
    salt=secrets.token_bytes(16)
    hashed='pbkdf2$210000$'+salt.hex()+'$'+hashlib.pbkdf2_hmac('sha256',password.encode(),salt,210000).hex()
    def quote(value):return "'"+value.replace("'","''")+"'"
    sql='INSERT INTO account(username,password_hash,display_name,role,status,version,created_at,updated_at) VALUES ('+','.join(quote(x) for x in [username,hashed,username,role,'ACTIVE'])+',0,now(),now()) RETURNING id;'
    # Authentication material is passed through stdin, never in command-line arguments or public output.
    compose(root,env,project,'exec','-T','database','psql','-U','library','-d','library','-v','ON_ERROR_STOP=1','-At',input=sql.encode(),capture_output=True)
    print('Staff account created:',username,role)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    default=Path(__file__).resolve().parent.parent
    p.add_argument('--package-dir',type=Path,default=default)
    p.add_argument('--env-file',type=Path)
    p.add_argument('--project',default='mls-p5-local')
    sub=p.add_subparsers(dest='action',required=True)
    for action in ['verify','load-images','start','stop','restart']:sub.add_parser(action)
    a=sub.add_parser('init');a.add_argument('--port',type=int,default=8088)
    a=sub.add_parser('backup');a.add_argument('path',type=Path)
    a=sub.add_parser('restore');a.add_argument('path',type=Path);a.add_argument('--target-env',type=Path,required=True);a.add_argument('--target-project',required=True)
    a=sub.add_parser('bootstrap');a.add_argument('--username',required=True);a.add_argument('--role',choices=['ADMIN','LIBRARIAN'],required=True)
    args=p.parse_args();root=args.package_dir.resolve();env=(args.env_file or root/'config/runtime.env').resolve()
    project_name(args.project)
    if args.action=='verify':verify(root)
    elif args.action=='init':init(root,env,args.port)
    elif args.action=='load-images':
        verify(root)
        invoke(['docker','load','--input',str(root/'images.tar.gz')])
        for entry in json.loads((root/'images.json').read_text()):
            actual=invoke(['docker','image','inspect','--format','{{.Id}}',entry['tag']],capture_output=True,text=True).stdout.strip()
            if actual!=entry['id']:raise ValueError('Loaded image identity mismatch: '+entry['tag'])
    elif args.action=='start':compose(root,env,args.project,'up','-d','--no-build','--wait','--wait-timeout','180')
    elif args.action=='stop':compose(root,env,args.project,'stop')
    elif args.action=='restart':restart(root,env,args.project)
    elif args.action=='backup':backup(root,env,args.project,args.path.resolve())
    elif args.action=='restore':restore(root,env,args.project,args.path.resolve(),args.target_env.resolve(),args.target_project)
    elif args.action=='bootstrap':bootstrap(root,env,args.project,args.username,args.role)

if __name__=='__main__':
    try:main()
    except (ValueError,RuntimeError,OSError) as error:
        print(str(error),file=sys.stderr);raise SystemExit(1)
