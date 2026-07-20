# Production Authentication and Payment Setup

## 1. Public origins

Set `FRONTEND_URL` to the exact HTTPS CloudFront application origin. Set `CORS_ALLOWED_ORIGINS` to the same origin (and any additional trusted frontend origin, comma-separated). Do not use `*` because secure refresh cookies require credentialed CORS.

## 2. Refresh cookies

Production uses `SameSite=None` and `Secure=true` because CloudFront and Elastic Beanstalk are different browser sites. Both are required for the browser to send the HttpOnly refresh and device-fingerprint cookies over a cross-site HTTPS API call.

## 3. Google and GitHub OAuth

The provider callback must be the public HTTPS backend endpoint, not the frontend homepage:

- `https://YOUR_PUBLIC_BACKEND/login/oauth2/code/google`
- `https://YOUR_PUBLIC_BACKEND/login/oauth2/code/github`

Set `GOOGLE_OAUTH2_REDIRECT_URI` and `GITHUB_OAUTH2_REDIRECT_URI` to those exact values. Add the same exact values in each provider console. `FRONTEND_URL` is only where the completed browser session returns after the backend has issued secure cookies.

If CloudFront fronts the backend as well, route `/oauth2/*` and `/login/oauth2/*` to Elastic Beanstalk without rewriting or caching them. The configured callback host must be reachable over HTTPS.

## 4. Razorpay readiness

Before submitting the website for review, make these public routes available from the production frontend:

- `/terms`
- `/privacy-policy`
- `/payment-policy`
- `/contact`
- `/#pricing`

Set `VITE_SUPPORT_EMAIL` in the frontend deployment to a mailbox that is monitored by your team. Replace any business/legal details only after they are verified for the business entity.

Keep `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` in Elastic Beanstalk environment properties only. Configure the webhook endpoint as `https://YOUR_PUBLIC_BACKEND/api/payment/webhook` and use a separate webhook secret. The backend verifies both client payment signatures and webhook signatures, while reconciliation protects wallet credits if the browser closes or a server restart happens during payment completion.

## 5. Deployment check

1. Deploy backend environment properties and restart the environment.
2. Deploy frontend with `VITE_API_BASE_URL` using the public HTTPS backend origin and `VITE_SUPPORT_EMAIL` set.
3. Open the site in a private window, log in, refresh the page twice, and confirm the session remains active.
4. Confirm provider redirects use HTTPS and match the registered callback exactly.
5. Test Razorpay in test mode before using live keys.