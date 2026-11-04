"""Recover the account-manager source slice from Mellon's original binary.

Run Vineflower first into .tools/recovered. This script records the source
selection; the maintained, backported source lives in src/main/java.
"""
import json
import re
from pathlib import Path

recovered = Path('.tools/recovered')
destination = Path('src/main/java')
sources = {'.'.join(p.relative_to(recovered).with_suffix('').parts): p
           for p in recovered.rglob('*.java')}
roots = ['autismclient.AutismAccountManagerMod',
         'autismclient.mixin.AutismAccountsOnlyMixin',
         'autismclient.mixin.AutismAccountFeedbackMixin',
         'autismclient.mixin.accessor.AutismAccountMinecraftAccessor']
seen = set()
pending = list(roots)
while pending:
    name = pending.pop()
    if name in seen:
        continue
    seen.add(name)
    original = sources[name]
    target = destination / original.relative_to(recovered)
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(original.read_text(encoding='utf-8'), encoding='utf-8')
    text = target.read_text(encoding='utf-8')
    package = name.rsplit('.', 1)[0]
    dependencies = set()
    for token in re.findall(r'\b[A-Z][A-Za-z0-9_]*\b', text):
        if package + '.' + token in sources:
            dependencies.add(package + '.' + token)
    for token in re.findall(r'\b(?:autismclient|net\.minecraft\.client\.renderer\.rendertype)\.[A-Za-z0-9_.]+', text):
        while '.' in token:
            if token in sources:
                dependencies.add(token)
                break
            token = token.rsplit('.', 1)[0]
    pending.extend(dependencies - seen)
Path('tools/recovered-source-selection.json').write_text(
    json.dumps(sorted(str(sources[n].relative_to(recovered)).replace('\\', '/')
                      for n in seen), indent=2) + '\n', encoding='utf-8')
print(f'{len(seen)} source files selected')
