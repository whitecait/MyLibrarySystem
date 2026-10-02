#!/usr/bin/env python3
"""Compare actual successful Flyway version order with the source migration manifest."""
import argparse
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parent.parent

def expected_versions(directory):
    versions=[]
    for path in Path(directory).glob('V*__*.sql'):
        match=re.fullmatch(r'V([0-9]+(?:[._][0-9]+)*)__.+\.sql',path.name)
        if not match:raise ValueError('Invalid versioned migration filename: '+path.name)
        version=match[1].replace('_','.')
        versions.append(version)
    if not versions or len(versions)!=len(set(versions)):
        raise ValueError('Versioned migration manifest is empty or contains duplicate versions')
    return sorted(versions,key=lambda x:tuple(int(part) for part in x.split('.')))

def compare(directory,actual):
    expected=expected_versions(directory)
    if actual.split(',')!=expected:
        raise ValueError('Flyway versions mismatch: expected '+','.join(expected)+'; actual '+actual)
    return expected

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--directory',type=Path,default=ROOT/'library-server/src/main/resources/db/migration')
    p.add_argument('--actual',required=True)
    args=p.parse_args()
    try:compare(args.directory,args.actual)
    except ValueError as error:p.exit(1,str(error)+'\n')
    print('Flyway version manifest matches successful installed migrations.')

if __name__=='__main__':main()
