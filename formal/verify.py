#!/usr/bin/env python3
"""Fail-closed bounded model checking. See README.md for abstractions and evidence."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.request

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def tool_jar(explicit=None):
    lock = json.loads((HERE / 'toolchain.json').read_text())
    target = Path(explicit).resolve() if explicit else HERE / '.cache/tla2tools.jar'
    if not target.exists():
        if explicit:
            raise ValueError('Explicit TLC jar does not exist')
        target.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(lock['url'], timeout=60) as response:
            data = response.read(32 * 1024 * 1024)
        if hashlib.sha256(data).hexdigest() != lock['sha256']:
            raise ValueError('Downloaded TLC checksum mismatch')
        temporary = target.with_suffix('.download')
        temporary.write_bytes(data)
        temporary.replace(target)
    if sha(target) != lock['sha256']:
        raise ValueError('TLC checksum mismatch')
    return target

def verdict(code, output, kind, expected=None):
    if kind == 'safety':
        return code == 0 and 'Model checking completed. No error has been found.' in output
    # TLC v1.7.4 invariant violation codes: initial state 11, behavior 12.
    return (code in (11, 12)
            and re.search(r'Invariant\s+' + re.escape(expected or '') + r'\s+is violated\.', output) is not None
            and re.search(r'State\s+1\s*:', output) is not None
            and not re.search(r'Parsing or semantic analysis failed|Error:.*(?:exception|syntax)', output, re.I))

def run_case(jar, model, config, directory, kind, expected=None, timeout=180):
    directory.mkdir(parents=True, exist_ok=True)
    staged_model = directory / model.name
    staged_config = directory / 'input.cfg'
    staged_model.write_bytes(model.read_bytes())
    staged_config.write_bytes(config.read_bytes())
    command = ['java', '-cp', str(jar), 'tlc2.TLC', '-workers', '1', '-deadlock',
               '-cleanup', '-metadir', str(directory / 'states'), '-config', staged_config.name, staged_model.name]
    start = time.monotonic()
    try:
        result = subprocess.run(command, cwd=directory, text=True, encoding='utf-8', errors='replace',
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
        code, output = result.returncode, result.stdout
        passed = verdict(code, output, kind, expected)
    except subprocess.TimeoutExpired as error:
        code = None
        output = error.stdout or b''
        if isinstance(output, bytes): output = output.decode('utf-8', errors='replace')
        output += '\nRUNNER: timeout; this case failed.\n'
        passed = False
    except OSError as error:
        code, output, passed = None, 'RUNNER: tool execution failed: ' + str(error), False
    (directory / 'tlc.log').write_text(output)
    return {'kind': kind, 'expected': expected, 'passed': passed, 'exit_code': code,
            'elapsed_seconds': round(time.monotonic() - start, 3),
            'model_sha256': sha(model), 'config_sha256': sha(config),
            'log': str((directory / 'tlc.log').relative_to(directory.parent.parent)),
            'log_sha256': sha(directory / 'tlc.log'), 'command': command}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--tool-jar')
    parser.add_argument('--output-dir', default=str(HERE / 'output'))
    args = parser.parse_args()
    out = Path(args.output_dir).resolve()
    # Evidence is append-only by run: refusing reuse prevents mixing old and new results.
    if out.exists() and any(out.iterdir()):
        parser.error('Output directory is not empty; choose a new run directory')
    out.mkdir(parents=True, exist_ok=True)
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    dirty = subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT)
    report = {'checkout_commit': head, 'pr_head_commit': os.environ.get('PR_HEAD_SHA', head),
              'working_tree_dirty': bool(dirty), 'toolchain': json.loads((HERE/'toolchain.json').read_text()),
              'inputs': {}, 'checks': [], 'cases': [], 'passed': False,
              'boundary': 'Safety and bounded reachability only; no full-system or liveness proof.'}
    for base in [HERE, ROOT/'docs/02-overall-design']:
        for path in sorted(base.rglob('*')):
            if path.is_file() and not any(x in path.parts for x in ['output','.cache','__pycache__']):
                report['inputs'][str(path.relative_to(ROOT))] = sha(path)
    try:
        jar = tool_jar(args.tool_jar)
        report['java_version'] = subprocess.check_output(['java','-version'], stderr=subprocess.STDOUT, text=True).strip()
        report['python_version'] = sys.version
        java_major = re.search(r'version "(\d+)', report['java_version'])
        if sys.version_info < (3, 12) or not java_major or int(java_major[1]) < 21:
            raise ValueError('Python 3.12+ and Java 21+ are required')
        checks = [
            [sys.executable, str(HERE/'checks/extract_check.py'),
             'docs/02-overall-design/MLS-DOC-OD-0002.md','docs/02-overall-design/MLS-DOC-OD-0003.md',
             '--out',str(out/'structural-report.md')],
            [sys.executable,str(HERE/'checks/tables_check.py'),'--out',str(out/'tables-report.md')],
            [sys.executable,'-m','unittest','discover','-s',str(HERE/'checks'),'-p','test_*.py'],
            [sys.executable,'-m','unittest','discover','-s',str(HERE/'tests'),'-p','test_*.py'],
        ]
        env = dict(os.environ, TLA_TOOLS_JAR=str(jar))
        for index, command in enumerate(checks):
            result = subprocess.run(command, cwd=ROOT, env=env, text=True, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, timeout=180)
            (out/f'check-{index}.log').write_text(result.stdout)
            report['checks'].append({'command':command,'exit_code':result.returncode,'passed':result.returncode==0})
            print(f'Check {index}: {"PASS" if result.returncode==0 else "FAIL"}', flush=True)
        for group in json.loads((HERE/'suite.json').read_text()):
            model = HERE/'models'/f'{group["model"]}.tla'
            base_config = HERE/'models'/group['config']
            declared = re.findall(r'^INVARIANT\s+(\w+)',base_config.read_text(),re.M)
            declared_probes = re.findall(r'^(Probe\w+)\s*==',model.read_text(),re.M)
            if declared != group['safety'] or set(declared_probes) != set(group['probes']):
                raise ValueError('Suite/property declaration mismatch for '+group['model'])
            result = run_case(jar,model,base_config,out/'cases'/group['model'],
                              'safety',timeout=group['timeout_seconds'])
            result['name'] = group['model'];report['cases'].append(result)
            print(result['name']+': '+('PASS' if result['passed'] else 'FAIL'),flush=True)
            for probe in group['probes']:
                if not re.fullmatch(r'Probe\w+',probe): raise ValueError('Invalid probe name')
                directory=out/'cases'/f'{group["model"]}-{probe}';directory.mkdir(parents=True)
                config=directory/'probe.cfg'
                text=re.sub(r'^INVARIANT\s+\w+\s*$', '', base_config.read_text(),flags=re.M)
                config.write_text(text+'\nINVARIANT '+probe+'\n')
                result=run_case(jar,model,config,directory,'reachability',probe,group['timeout_seconds'])
                result['name']=group['model']+'-'+probe;report['cases'].append(result)
                print(result['name']+': '+('PASS' if result['passed'] else 'FAIL'),flush=True)
        report['passed']=all(x['passed'] for x in report['checks']+report['cases'])
    except Exception as error:
        report['error']=str(error)
        print('Formal verification FAILED: '+str(error),file=sys.stderr)
    finally:
        (out/'result.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
    return 0 if report['passed'] else 1

if __name__ == '__main__':
    raise SystemExit(main())
