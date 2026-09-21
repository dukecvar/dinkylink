# Dinky Link
A URL shortener.

## Summary
A user, Fred, finds a story on Yahoo! news and want to share with is followers on X but the URL is too long to share within a single tweet.  He copies the Yahoo! news link and goes to https://dinky.link, pastes the link into a text entry box on the page and either press enter or click "Get shortened link". The page responds with a shortened URL. He then copies the link and uses it as part of a post on X.

Followers of Fred see his X post and click on the dinky.link URL. The URL links to a dinky.link service which responds with a redirect (300) to the original Yahoo! news site.

## High Level Requirements
- A user can submit a long URL to a very simple web page and get a shortened URL back.
- The URL returned to the client is formatted like `https://dinky.link/<shortcode>` where a shortcode is a short unique system identifier. 
- Multiple requests with similar URLs will get the same shortened link in response
- Upon clicking on the shortened link, the user will be redirected to the original URL 
- The site should support >1k/second write requests
- The site should support >10k/second read requests

## Shortcodes
The shortcode needs to be long enough to be unique within the system but does not need to be unique externally.
To not need any URL encoding, the shortcode should be alpha-numeric character string including both capital and lowercase letters (ie. matching regex `[0-9A-Za-z]`).

### Hashing Algorithm
The hash function needs to be fast and deterministic but does not need to be cryptographically secure.
A hash of a URL using a 64 bit cypher is sufficiently large enough to create 8 & 10 character long base62 strings:
- 2^64  = 9,223,372,036,854,775,807
- 62^8 =        218,340,105,584,896
- 62^10 =   839,299,365,868,340,224

### shortcode generation
1. API creates a 128 bit hash of URL (urlHash) and calls insert procedure
2. SQL procedure creates an integer iterator for a salt (saltItr)
3. create a 64 bit hash of saltItr+urlHash
4. Convert to base62
5. Attempt to insert and fail on unique shortcode constraint which signifies collision
6. If collision, increment saltItr and repeat steps 3-5

## Design
### UI
- A website available at https://dinky.link
- The homepage is accessible at the webroot of the server (`GET /`)
- The homepage is a very basic web page written in simple HTML and JavaScript containing:
  - The name of the site and an icon on the top
  - A form containing:
    - A label with "Enter URL"
    - A text entry box that can accept a long URL
    - A button with text "Get Short URL".
  - Upon clicking "Get Short URL", the form will submit to `POST /`
  - The response will contain the shortened URL which will be displayed below the form


### API
2 external API endpoints should be externally/publicly available. They should be hosted by a gateway and proxy traffic to backend endpoints.
#### Create Shortcode
##### Request
```
POST /
{
  "url":"<input-url>"
}
```
##### Successful Response
HTTP response code 201 with body:
```
{
  "url":"<input-url>",
  "shortURL": "<hashed-url>"
}
```
##### Failure Response
```
{
  "url":"<input-url>",
  "error":"<reason>"
}
```
#### Use Shortcode
##### Request
`GET /<shortcode>` 
##### Successful Response
Returns response code 300 with `location=<original url>`
##### Failure Response
Returns response code 404. Page should be identical to home page including the input form except "Page Not Found" message shown above the form. 

## system Design
### Gateway
An nginx gateway is the single public entry point at `https://dinky.link`, sitting in front of the frontend and `backend/api` instances. `backend/workers` is internal-only and is never exposed through the gateway.

#### Routing
Routing is method + path based:

| Method | Path         | Routes to      | Purpose               |
|--------|--------------|-----------------|------------------------|
| GET    | `/`          | frontend        | Homepage               |
| POST   | `/`          | `backend/api`   | Create shortcode       |
| GET    | `/<shortcode>` | `backend/api` | Redirect to original URL |

#### Load Balancing
An `upstream` block lists all `backend/api` replicas, using `least_conn` so requests go to the instance with the fewest active connections. Passive health checks via `max_fails`/`fail_timeout` pull an instance out of rotation after repeated failures (open-source nginx doesn't support active health checks without nginx Plus).

#### Rate Limiting & Basic Security
Two `limit_req_zone`s keyed on client IP protect against per-client abuse (independent of the system-wide >1k/s write and >10k/s read throughput targets):

| Endpoint       | Limit                | Notes                          |
|----------------|-----------------------|---------------------------------|
| `POST /`       | 5 req/s, burst 10     | Write path, tighter limit       |
| `GET /<shortcode>` | 50 req/s, burst 100 | Read path, looser limit         |

A `limit_conn_zone` caps concurrent connections per IP. The gateway also enforces a small max request body size (payload is just a URL) and hides backend details from responses (`server_tokens off`, no internal hostnames leaked).

#### TLS Termination
nginx terminates HTTPS using a cert (Let's Encrypt/certbot), redirects HTTP→HTTPS, and proxies to backend services over plain HTTP internally.

#### Deployment
Runs as its own service in `local/docker-compose.yml` (matching the existing pattern where the frontend is nginx-served), sitting in front of the `frontend` and `backend/api` containers. The same nginx config drives prod, parameterized for cert paths and upstream addresses.

### Database
Postgresql should be used. A primary node should be used for DB writes and secondary node(s) should be used for read-only operations. Native active replication should be utilized to sync data between them.
Flyway should be used for migration scripts.
Initial schema should contain 1 table:

| records              |                |                     |
|----------------------|:--------------:|--------------------:|
| shortcode            |    char(8)     | primary key, unique |
| urlhash              |  binary(128)   |             indexed |
| url                  | varchar(2048)  |                     |
| lastTouchedTimeStamp |    datetime    |                     |

### Cache
Redis cache should be utilized as a caching engine. 3 buckets:

| cache bucket |    key    |    value  | notes                                                      |
|--------------|:---------:|----------:|------------------------------------------------------------|
| shortcodes   | urlhash   | shortcode | 1yr ttl updated when accessed                              | 
| urls         | shortcode |       url | 1yr ttl updated when accessed                              |
| last-touched | shortcode |  datetime | rotate 'live' for adding and 'flushing' for batch draining | 

### Worker(s)
#### Last touched worker
A job that flushes the last-touched cache and updates records' timestamps.
1. If 'live' bucket exists, rename 'live' to 'flushing+timestamp'
2. Loop until the flushing bucket is empty:
   1. Get 1000 records from bucket
   2. Update database: update lastTouchedTimeStamp for each record by shortcode
3. Sleep for 1 minute
#### Clean up worker
Once a day at lowest usage time (2 am by default), delete all records older than a year based on last touched.

