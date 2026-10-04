"""Ping de la lista de servidores de Minecraft (protocolo 1.20.1): versión, jugadores, MOTD y mods si es Forge."""
import socket, struct, json, sys
host = sys.argv[1] if len(sys.argv) > 1 else "216.163.187.40"
port = int(sys.argv[2]) if len(sys.argv) > 2 else 19001
def varint(n):
    out=b""
    while True:
        b=n&0x7F; n>>=7
        out+=bytes([b|(0x80 if n else 0)])
        if not n: return out
def readvar(s):
    n=0
    for i in range(5):
        b=s.recv(1)[0]; n|=(b&0x7F)<<(7*i)
        if not b&0x80: return n
s=socket.create_connection((host,port),timeout=8)
hs=varint(0)+varint(763)+varint(len(host))+host.encode()+struct.pack(">H",port)+varint(1)
s.sendall(varint(len(hs))+hs); s.sendall(b"\x01\x00")
readvar(s); readvar(s); l=readvar(s); data=b""
while len(data)<l: data+=s.recv(l-len(data))
j=json.loads(data); j.pop("favicon",None); print(json.dumps(j, ensure_ascii=False, indent=1)[:6000])
