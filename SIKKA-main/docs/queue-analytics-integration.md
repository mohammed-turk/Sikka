# Queue Analytics integration and deployment

This change preserves the stored operational status. Unknown rides are selectable and may appear in a provisional plan; a fresh confirmed closure or an explicit admin block is excluded. Availability assumptions and wait estimates are included in plan warnings.

## Railway backend variables

```text
TZ=Asia/Riyadh
SIKKA_QUEUE_ANALYTICS_ENABLED=true
SIKKA_QUEUE_ANALYTICS_URL=https://queueanalytics-production.up.railway.app
```

The integration is disabled by default for local/test execution. It polls once every five minutes while the backend is awake. No additional Railway service or database is required. Historical patterns are cached for 24 hours. Network requests run in the synchronization service, never inside planning transactions.

Source IDs are matched once using unambiguous normalized names/slugs and persisted as `queueAnalyticsRideId`. Subsequent updates use the numeric ID. Ambiguous or missing matches remain unlinked; they never guess another game.

Wait precedence: fresh valid source reading for today's visit; otherwise historical estimates for the visit weekday and hours; otherwise a labeled 15-minute default. Future visits never reuse today's live queue as a future prediction. Zero is a valid observed wait. Stale, future-dated, malformed, and invalid observations are rejected. Explicit administrative closures are not reopened by the feed.

The source API's last observation can be old outside park operating days. Old data is not relabeled as live and historical averages do not prove operating availability.

## Deployment order

1. Merge the shared backend changes into the matching backend variant (SMTP main or Railway/Brevo). Do not merge Brevo implementation files into SMTP main.
2. Configure the two variables above in the Railway backend. Apply them together with the intended backend deployment to avoid redeploying the old version first.
3. Deploy the Railway/Brevo backend. Hibernate adds optional source mapping/history fields under the existing `ddl-auto=update` configuration. No operational statuses are bulk rewritten.
4. Verify `/actuator/health`, public `/api/v1/ride/catalog`, and correct source mapping / reading timestamps after the first synchronization. Historical loading can take several source requests; until ready, labeled defaults are used.
5. Deploy the frontend branch containing the visit wizard and estimated availability labels.
6. Verify Solo creation → games → breaks → review; generate a provisional plan; confirm an actual fresh closure is excluded. Do not manufacture a fresh reading to pass this check.

No deployment is performed by creating these review branches. SMTP and Brevo branches retain their separate email implementations.
