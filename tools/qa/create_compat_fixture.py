"""Generate synthetic upstream-schema NBT/DPAPI records for local port checks.

The fixture is tied to the current Windows user, contains no real credentials,
and belongs under ignored .tools/. Never run it against an existing account file.
"""
import base64
import ctypes
import json
import struct
from ctypes import wintypes
from pathlib import Path


class Blob(ctypes.Structure):
    _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]


def seal(text):
    payload = text.encode('utf-8')
    memory = ctypes.create_string_buffer(payload)
    source = Blob(len(payload), ctypes.cast(memory, ctypes.POINTER(ctypes.c_ubyte)))
    target = Blob()
    crypt32 = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel32 = ctypes.WinDLL('kernel32', use_last_error=True)
    crypt32.CryptProtectData.argtypes = [ctypes.POINTER(Blob), wintypes.LPCWSTR,
                                        ctypes.c_void_p, ctypes.c_void_p,
                                        ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    crypt32.CryptProtectData.restype = wintypes.BOOL
    kernel32.LocalFree.argtypes = [ctypes.c_void_p]
    kernel32.LocalFree.restype = ctypes.c_void_p
    if not crypt32.CryptProtectData(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(target)):
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        return base64.b64encode(b'\x01' + ctypes.string_at(target.data, target.size)).decode('ascii')
    finally:
        kernel32.LocalFree(target.data)


def utf(text):
    data = text.encode('utf-8')
    return struct.pack('>H', len(data)) + data


def main():
    output = Path(__file__).resolve().parents[2] / '.tools/compat-fixture'
    output.mkdir(parents=True, exist_ok=True)
    file = output / 'autism-accounts.nbt'
    if file.exists():
        raise FileExistsError('Refusing to overwrite an existing fixture')
    expected = dict(id='c3c9fb66-323a-45c7-880d-001122334455', type='Microsoft',
                    username='CompatQA', uuid='11223344556677889900aabbccddeeff',
                    token='synthetic-mc-token', sessionToken='synthetic-session-token',
                    password='synthetic-password', label='synthetic-refresh-token')
    values = {key: expected[key] for key in ('id', 'type', 'username', 'uuid')}
    for field in ('token', 'sessionToken', 'password', 'label'):
        values['enc' + field[0].upper() + field[1:]] = seal(expected[field])
    data = b'\x0a' + utf('') + b'\x09' + utf('accounts') + b'\x0a' + struct.pack('>i', 1)
    for key, value in values.items():
        data += b'\x08' + utf(key) + utf(value)
    expected['sessionTokenExpiresAt'] = 1893456000000
    data += b'\x04' + utf('sessionExpiresAt') + struct.pack('>q', expected['sessionTokenExpiresAt']) + b'\x00\x00'
    file.write_bytes(data)
    (output / 'expected.json').write_text(json.dumps(expected, indent=2) + '\n', encoding='utf-8')
    print(f'Synthetic compatibility fixture: {output}')


if __name__ == '__main__':
    main()
