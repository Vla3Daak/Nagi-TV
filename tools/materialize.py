#!/usr/bin/env python3
"""Restore binary resources from text-safe source transport; never handles signing keys."""
from pathlib import Path
import base64
root=Path(__file__).resolve().parents[1]
for folder in ('res',):
 for p in (root/folder).rglob('*.b64'):
  data=base64.b64decode(p.read_bytes(),validate=True)
  p.with_suffix('').write_bytes(data)
print('Android resources materialized.')
