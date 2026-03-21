# qBittorrent Web API — notes

**In-app (Android):** **Settings → qBittorrent Web UI** stores base URL + optional credentials; on a **release** (torrent group) screen, **Send to qBittorrent** downloads the `.torrent` from Redacted and POSTs it to `/api/v2/torrents/add` (see [QbittorrentWebClient.kt](../android/app/src/main/java/com/turntable/barcodescanner/QbittorrentWebClient.kt)). Cleartext HTTP is allowed for typical LAN URLs (`network_security_config`).

This document also records the **Python** client for scripts and automation.

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

## Possible extra integration directions

1. **Companion service (LAN)** — Small Python service using `qbittorrent-api` for richer workflows (magnets, categories, etc.).
2. **Export flow** — Save to a watched folder or trigger a script that uses `qbittorrent-api`.

## Security

If the Web API is reachable beyond localhost, use strong credentials, HTTPS, and firewall rules. See qBittorrent’s own guidance for exposing the Web UI.
