# qBittorrent Web API — future implementation notes

**Not implemented in turnTable yet.** This document records the intended API surface for future work (e.g. companion scripts, home-lab automation, or in-app HTTP calls to a local qBittorrent Web UI).

## References

| Resource | URL |
|----------|-----|
| **Python client (recommended for scripts)** | [qbittorrent-api documentation](https://qbittorrent-api.readthedocs.io/en/latest/) |
| **PyPI package** | `pip install qbittorrent-api` ([project on PyPI](https://pypi.org/project/qbittorrent-api/)) |
| **qBittorrent Web UI** | Enable in the desktop app: **Tools → Preferences → Web UI** |

The Python library implements the **qBittorrent Web API** (cookie-based auth, namespaces such as `auth`, `app`, `torrents`, `transfer`, `rss`, `search`, etc.). It handles version differences and session refresh.

## Quick start (Python)

```bash
python -m pip install qbittorrent-api
```

```python
import qbittorrentapi

conn_info = dict(
    host="localhost",
    port=8080,
    username="admin",
    password="adminadmin",
)
with qbittorrentapi.Client(**conn_info) as client:
    client.torrents.add(urls="...", torrent_files="/path/to/file.torrent")
    # or client.torrents.add(torrent_files=open("file.torrent", "rb"))
```

See the upstream docs (**API Reference → Torrents**, `torrents_add`, etc.) for full endpoint coverage.

## Possible turnTable integration directions

1. **Companion service (LAN)** — Small Python (or other) service using `qbittorrent-api` that accepts `.torrent` bytes or magnet links from the phone (HTTPS + token) and calls `torrents_add`.
2. **Direct Web API from Android** — Mirror the same HTTP endpoints the Web UI uses (OkHttp, multipart for files). The Python client’s behavior and request shapes are a useful reference; no Python runtime required on device.
3. **Export flow** — Keep current “save / share `.torrent`” behavior; document a user workflow: save to a watched folder or trigger a script that uses `qbittorrent-api`.

## Security

If the Web API is reachable beyond localhost, use strong credentials, HTTPS, and firewall rules. See qBittorrent’s own guidance for exposing the Web UI.
