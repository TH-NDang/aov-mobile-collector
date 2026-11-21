# Invitation reward counting (0.13.29)

The invitation task reads the amount on the verified central **Vé nhảy** receipt, not the event balance, daily 0/1 quota, or advertised 6–38 range. Focused 3× OCR retains line and element coordinates. Missing or contradictory quantities remain unknown. Two agreeing observations are required; at most four receipt observations are attempted without pressing confirmation again.

The exact receipt image is retained before committing an account result. A visible ticket receipt confirms one gift, independently of whether its quantity is readable. A changed daily quota without a receipt confirms neither a gift nor a zero quantity.

Schema 2 stores unknown quantities as JSON null. Summaries show known amounts and the number of unresolved results rather than presenting a partial sum as a complete total. Legacy done/used results with zero ticket quantities (including indistinguishable skipped accounts and unseen receipts) are conservatively treated as unknown; existing positive counts and all completion/submission checkpoints are preserved. Upgrading does not replay completed accounts or reconstruct earlier quantities from the last screenshot.

Regression coverage includes anchored/merged OCR, contradictory and unrelated numbers, missing quantities, mixed totals, legacy state, bounded rereads, pending submission resume, duplicate popup/resume, and a synthetic on-device OCR receipt. No user screenshots are included. Existing IME safety and Pro behavior are unchanged.
