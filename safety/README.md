# :safety — redaction firewall + audit (P3)

Builds in **Phase 3**. Will own privacy and accountability:

- Redaction firewall: password-node drop, protected-app masking, regex/PII
  masks (Luhn cards, OTP, IBAN, custom), payload inspection
- Audit log (Room): every action, evidence, redaction count, route/failover
- Confirmation-gate policy + protected-apps policy evaluation
- Usage accounting feeding the P4 dashboard

Depends on `:kernel` only. No screen/notification content may leave the
device without passing through this module (unit-tested in P3).
