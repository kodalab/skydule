# Google Play Data safety answers (draft, for the Play Console form)

**Does the app collect or share any of the required user data types?** Collects: Yes (see below). Shares: No.

| Data type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|
| Approximate location | Yes (optional) | No | App functionality (weather) | Sent to Open-Meteo, not stored. Treat as "collected, processed ephemerally". |
| Calendar events | Processed on-device only | No | App functionality | Never leaves the device, so not "collected" per Play's definition. |
| Other user-generated content (location text typed into events) | Yes (optional) | No | App functionality (place suggestions) | Sent to the Photon geocoder; ephemeral. |

- Data encrypted in transit: **Yes** (HTTPS).
- Users can request deletion: no data is held by us; uninstalling removes local data.
- No ads, no analytics, no account creation.
- Declare the permission use for READ/WRITE_CALENDAR and USE_EXACT_ALARM (calendar/reminder app core function) in the Play permissions declaration.
- Privacy policy URL: host `docs/PRIVACY.md` as a public web page (e.g. GitHub Pages) and enter its address.
