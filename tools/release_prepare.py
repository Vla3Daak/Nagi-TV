import base64,hashlib,json,re,os
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=json.loads((root/'.release/request.json').read_text())
tag=r['tag']; assert re.fullmatch(r'v\d+\.\d+\.\d+',tag)
name=f'Nagi-TV-{tag}.apk'; data=base64.b64decode((root/'.release'/f'{name}.b64').read_bytes(),validate=True)
assert hashlib.sha256(data).hexdigest()==r['sha256'], 'APK checksum mismatch'
(root/name).write_bytes(data)
(root/'SHA256SUMS.txt').write_text(f"{r['sha256']}  {name}\n")
if os.getenv('GITHUB_OUTPUT'):
 with open(os.environ['GITHUB_OUTPUT'],'a')as f:f.write(f'tag={tag}\nname={name}\n')
print('Signed release payload verified:',name,len(data))
