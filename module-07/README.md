# Module 7: Authentication and Transport Security

This module covers testing protected APIs, including everything that must happen when credentials are missing, wrong or not enough: Basic authentication, OAuth 2.0 bearer tokens, the error responses of RFC 6749 and RFC 6750, sessions with CSRF, TLS and mutual TLS, and credentials on redirects.

## Table of Contents

- [Module 7: Authentication and Transport Security](#module-7-authentication-and-transport-security)
  - [Table of Contents](#table-of-contents)
  - [The Domain: a Protected Bank](#the-domain-a-protected-bank)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: Basic Authentication](#section-1-basic-authentication)
    - [Section 2: Bearer Tokens](#section-2-bearer-tokens)
    - [Section 3: Negative Security Tests](#section-3-negative-security-tests)
    - [Section 4: Sessions and CSRF](#section-4-sessions-and-csrf)
    - [Section 5: TLS and Mutual TLS](#section-5-tls-and-mutual-tls)
    - [Section 6: Credentials on Redirects](#section-6-credentials-on-redirects)

## The Domain: a Protected Bank

[`BankAuthApi`](src/main/java/com/example/auth/BankAuthApi.java) is an OAuth 2.0 authorization server and the resource server it protects, in one process, built on the JDK only. [`JwtService`](src/main/java/com/example/auth/JwtService.java) issues RS256 JWT access tokens (RFC 9068 style) and publishes the key as a JWKS.

| Endpoint | Rules |
| --- | --- |
| `POST /oauth2/token` | Client credentials grant; `client_secret_basic` or `client_secret_post`. Clients: `tpp-accounts`/`accounts-secret` (`accounts:read`), `tpp-payments`/`payments-secret` (`accounts:read`, `payments:write`) |
| `GET /.well-known/jwks.json` | The public signing key |
| `GET /api/v1/accounts`, `POST /api/v1/payments` | `accounts:read`, `payments:write`; audience `accounts-api` |
| `GET /admin/health` | Basic `admin`/`changeit`; challenges with 401 |
| `GET /admin/report` | The same credentials, but 404 without them |
| `GET`/`POST /portal/login`, `GET /portal/home` | An HTML login form with a CSRF token, then a `JSESSIONID` session |
| `GET /api/v1/partner-redirect` | 302 to a partner server on another port |

[`SecureAccountsApi`](src/main/java/com/example/auth/SecureAccountsApi.java) serves `GET /api/v1/whoami` over HTTPS, optionally requiring a client certificate. The PKCS#12 key material (password `changeit`, valid until 2036) is in `src/main/resources/tls` (server) and `src/test/resources/tls` (client).

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-07` | 34 tests pass |

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. Basic Authentication | [Basic Authentication in REST Assured: Challenged Versus Preemptive](../docs/module-07-section-01-rest-assured-basic-authentication.md) |
| 2. Bearer Tokens | [OAuth 2.0 Bearer Tokens in REST Assured: The Client Credentials Grant and auth().oauth2(..)](../docs/module-07-section-02-rest-assured-oauth2-bearer-tokens.md) |
| 3. Negative Security Tests | [Negative Security Tests in REST Assured: Missing, Invalid, Expired and Insufficient Tokens](../docs/module-07-section-03-rest-assured-negative-security-tests.md) |
| 4. Sessions and CSRF | [Sessions and CSRF in REST Assured: SessionFilter, htmlPath and a Form Login](../docs/module-07-section-04-rest-assured-sessions-and-csrf.md) |
| 5. TLS and Mutual TLS | [TLS and Mutual TLS in REST Assured: Trust Stores, Key Stores and an SSLContext](../docs/module-07-section-05-rest-assured-tls-and-mutual-tls.md) |
| 6. Credentials on Redirects | [Credentials on Redirects in REST Assured: Sensitive Headers Stripped Across Origins](../docs/module-07-section-06-rest-assured-credentials-on-redirects.md) |

## Sections

### Section 1: Basic Authentication

**Guide:** [module-07-section-01-rest-assured-basic-authentication.md](../docs/module-07-section-01-rest-assured-basic-authentication.md)

| Class | What it tests |
| --- | --- |
| [`BasicAuthenticationTest`](src/test/java/com/example/module_07/section_01/BasicAuthenticationTest.java) | The challenge, challenged and preemptive Basic, a server that never challenges, and wrong credentials |

**REST Assured concepts**

- `auth().basic(..)` waits for a 401 challenge before sending credentials; `auth().preemptive().basic(..)` sends them at once, which is what APIs usually need.

### Section 2: Bearer Tokens

**Guide:** [module-07-section-02-rest-assured-oauth2-bearer-tokens.md](../docs/module-07-section-02-rest-assured-oauth2-bearer-tokens.md)

| Class | What it tests |
| --- | --- |
| [`BearerTokenTest`](src/test/java/com/example/module_07/section_02/BearerTokenTest.java) | The token response, `client_secret_post`, `auth().oauth2(token)` and a plain header, and reading the claims of the JWT |

**REST Assured concepts**

- The token request is a form post; the token is fetched once per class in `@BeforeAll` and reused.

### Section 3: Negative Security Tests

**Guide:** [module-07-section-03-rest-assured-negative-security-tests.md](../docs/module-07-section-03-rest-assured-negative-security-tests.md)

| Class | What it tests |
| --- | --- |
| [`NegativeSecurityTest`](src/test/java/com/example/module_07/section_03/NegativeSecurityTest.java) | Token endpoint errors (`invalid_client`, `unsupported_grant_type`, `invalid_scope`); every endpoint without a token (parameterized); expired, wrong-audience and tampered tokens; a missing scope; the right scope |

**REST Assured concepts**

- RFC 6750: no error code when no token was sent, `error="invalid_token"` for a bad token, 403 with `error="insufficient_scope"` and the needed `scope`.
- `@Nested` classes group the token endpoint and the resource server; `@ParameterizedTest` with a `Method` enum covers every endpoint.

### Section 4: Sessions and CSRF

**Guide:** [module-07-section-04-rest-assured-sessions-and-csrf.md](../docs/module-07-section-04-rest-assured-sessions-and-csrf.md)

| Class | What it tests |
| --- | --- |
| [`SessionAndCsrfTest`](src/test/java/com/example/module_07/section_04/SessionAndCsrfTest.java) | Logging in and using the session, no session, setting the session id explicitly, a missing CSRF token, and a reused one |

**REST Assured concepts**

- A `SessionFilter` remembers `JSESSIONID` across requests; `htmlPath()` reads the CSRF token from the login page. `auth().form(..)` with `CsrfConfig` can do these steps for you.

### Section 5: TLS and Mutual TLS

**Guide:** [module-07-section-05-rest-assured-tls-and-mutual-tls.md](../docs/module-07-section-05-rest-assured-tls-and-mutual-tls.md)

| Class | What it tests |
| --- | --- |
| [`TlsAndMutualTlsTest`](src/test/java/com/example/module_07/section_05/TlsAndMutualTlsTest.java) | An untrusted certificate, relaxed validation, a trust store, mutual TLS without and with a client certificate, and a JDK `SSLContext` |

**REST Assured concepts**

- `relaxedHTTPSValidation()` trusts everything; `sslConfig().trustStore(..)` trusts one certificate and keeps the host name check. The shortcut `given().trustStore(..)` also turns the host name check off.
- Since REST Assured 6.2 a key store is used for mutual TLS even without a trust store; since 6.3 `sslConfig().sslContext(..)` takes the JDK's own `SSLContext`.

### Section 6: Credentials on Redirects

**Guide:** [module-07-section-06-rest-assured-credentials-on-redirects.md](../docs/module-07-section-06-rest-assured-credentials-on-redirects.md)

| Class | What it tests |
| --- | --- |
| [`CredentialsOnRedirectTest`](src/test/java/com/example/module_07/section_06/CredentialsOnRedirectTest.java) | `Authorization` and `Cookie` dropped on a redirect to another origin, and the old behaviour turned back on |

**REST Assured concepts**

- Since REST Assured 6.1, sensitive headers are stripped when a redirect changes scheme, host or port. Before, the token followed the redirect.
