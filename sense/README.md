# :sense — perception (P2)

Builds in **Phase 2**. Will own everything AXIS perceives:

- `AxisAccessibilityService` + node-tree snapshots / semantic screen builder
- `AxisNotificationListener` (parse, quick-reply, OTP watch, prioritization)
- MediaProjection frame capture + tess-two OCR + OpenCV template matching
- Sensor / context feeds (battery, BT, Wi-Fi SSID, motion, light, calls/SMS,
  calendar, geofence, foreground-app)
- Permission monitor + watchdog + heartbeat (with `:kernel`)

Depends on `:kernel` only. `:app` wires the services in its manifest.
