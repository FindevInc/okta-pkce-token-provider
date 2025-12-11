# Okta Token Provider

A Java library for obtaining OAuth 2.0 access tokens from Okta using the Authorization Code flow with PKCE (Proof Key for Code Exchange).

## Features

- **Automated Token Retrieval**: Handles the complete OAuth 2.0 authorization flow
- **Token Caching**: Built-in caching mechanism to reduce API calls
- **PKCE Support**: Implements PKCE for enhanced security
- **Configurable Expiration**: Customizable token cache expiration
- **Thread-Safe**: Prepared for concurrent access

## OAuth 2.0 Authorization Code with PKCE vs Standard Authorization Code Flow

### What is PKCE?

PKCE (Proof Key for Code Exchange, pronounced "pixy") is an extension to the OAuth 2.0 Authorization Code flow that provides additional security, particularly for public clients like mobile and single-page applications.

### Key Differences

#### Standard Authorization Code Flow (grant_type=authorization_code)
- **Client Secret Required**: Relies on a client secret to authenticate the client application
- **Security Risk**: Client secrets can be extracted from mobile apps or browser-based applications
- **Best For**: Confidential clients (server-side applications) that can securely store secrets
- **Token Exchange**: Uses `client_id` + `client_secret` to exchange authorization code for tokens

#### Authorization Code with PKCE Flow
- **No Client Secret Needed**: Uses dynamically generated code verifier and code challenge instead
- **Enhanced Security**: Prevents authorization code interception attacks
- **Best For**: Public clients (mobile apps, SPAs, native apps) and increasingly recommended for all OAuth flows
- **Token Exchange**: Uses `client_id` + `code_verifier` to exchange authorization code for tokens
- **Okta Recommendation**: Okta recommends PKCE for all authorization code flows, even for confidential clients

### PKCE Flow Steps (as implemented in this library)

1. **Generate Code Verifier**: Create a cryptographically random string (43-128 characters)
2. **Generate Code Challenge**: SHA-256 hash the code verifier and Base64-URL encode it
3. **Authorization Request**: Send code challenge with authorization request
4. **Receive Authorization Code**: User authenticates and grants permission
5. **Token Exchange**: Exchange authorization code + original code verifier for access token
6. **Validation**: Okta verifies that SHA-256(code_verifier) matches the original code challenge

### Why This Library Uses PKCE

This implementation uses PKCE because:
- **Security Best Practice**: Okta recommends PKCE for all OAuth 2.0 flows
- **No Client Secret Management**: Eliminates the need to securely store and manage client secrets
- **Protection Against Attacks**: Prevents authorization code interception and replay attacks
- **Future-Proof**: Aligns with modern OAuth 2.0 security recommendations (RFC 7636)

### Okta-Specific Considerations

- **PKCE Support**: Okta fully supports PKCE for all authorization servers
- **Configuration**: PKCE can be required or optional in Okta application settings
- **Token Endpoint**: The token endpoint URL is the same for both flows (`/oauth2/{authServerId}/v1/token`)
- **Grant Type**: Both flows use `grant_type=authorization_code`, but PKCE adds `code_verifier` parameter

### User Details and Claims Differences

One important distinction between these flows in Okta:

#### Standard Authorization Code (grant_type=authorization_code)
- **Limited User Information**: Typically returns minimal user details in the access token
- **Scope Limitations**: May not include user profile scopes like `email`, `profile` by default
- **ID Token**: Often doesn't include comprehensive user claims without additional configuration
- **Use Case**: Primarily for service-to-service authentication where user context isn't required

#### Authorization Code with PKCE (this library)
- **Rich User Information**: Includes comprehensive user details when proper scopes are requested
- **User Claims**: Returns email, username, name, and other profile information
- **Scopes Used**: This library requests `openid profile offline_access email` scopes
- **ID Token**: Provides detailed user claims in both access and ID tokens
- **User Context**: Essential for applications that need to identify and personalize for the authenticated user

**Example User Details Available with PKCE:**
```json
{
  "sub": "00u1abc2def3ghi4jkl5",
  "lastName": "Goodwin",
  "firstName": "Jim",
  "loginId": "jim_goodwin",
  "email": "jim_goodwin@company.com",
  "user_groups": [
    "REVIEWERS", "MANAGERS"
  ]
}
```

## Prerequisites

- Java 8 or higher
- Okta account with appropriate OAuth 2.0 configuration
- Valid Okta credentials (username and password)

## Dependencies

This library uses the following dependencies:

- OkHttp3 - HTTP client
- Guava - Caching and utilities
- Gson - JSON parsing
- Apache Commons Lang3 - String utilities
- JSoup - HTML parsing

## Installation

Add the project to your Java application. Ensure all dependencies are included in your build configuration (Maven/Gradle).

## Configuration

You'll need the following Okta configuration parameters:

- **oktaUrl**: Your Okta organization URL (e.g., `https://your-domain.okta.com`)
- **oktaUsername**: Okta user username
- **oktaPassword**: Okta user password
- **identityZoneId**: Okta authorization server ID
- **targetClientId**: OAuth 2.0 client ID
- **loginRedirectUri**: Redirect URI configured in your Okta application

## Usage

### Basic Usage

```java
OktaTokenProvider tokenProvider = new OktaTokenProvider(
    "https://your-domain.okta.com",
    "username@example.com",
    "password",
    "your-identity-zone-id",
    "your-client-id",
    "https://your-app.com/callback"
);

// Get a cached token (or fetch a new one if cache is empty/expired)
String accessToken = tokenProvider.getToken();
```

### Custom Configuration

```java
OkHttpClient customClient = new OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .build();

OktaTokenProvider tokenProvider = new OktaTokenProvider(
    customClient,
    "https://your-domain.okta.com",
    "username@example.com",
    "password",
    "your-identity-zone-id",
    "your-client-id",
    "https://your-app.com/callback",
    Duration.ofMinutes(55) // Custom cache expiration
);
```

### Advanced Operations

```java
// Force refresh token (bypass cache)
String freshToken = tokenProvider.getNewToken();

// Use multiple token keys for different contexts
String tokenForContext1 = tokenProvider.getToken("context1");
String tokenForContext2 = tokenProvider.getToken("context2");

// Expire specific tokens
tokenProvider.expireKeys(Arrays.asList("context1", "context2"));

// Expire all cached tokens
tokenProvider.expireAll();
```

## How It Works

1. **Session Token Retrieval**: Authenticates with Okta using username/password
2. **Authorization Code Generation**: Generates PKCE code challenge and obtains authorization code
3. **Access Token Exchange**: Exchanges authorization code for access token
4. **Caching**: Stores token in cache with configurable expiration (default: 3500 seconds)

## Security Considerations

- Store credentials securely (use environment variables or secret management)
- Use HTTPS for all communications
- Rotate passwords regularly
- Consider using OAuth 2.0 client credentials flow for service-to-service authentication
- The default cache expiration is set to 3500 seconds (just under 1 hour)

## Error Handling

The library throws exceptions for various error conditions:

```java
try {
    String token = tokenProvider.getToken();
} catch (Exception e) {
    // Handle authentication errors, network issues, etc.
    e.printStackTrace();
}
```

## Thread Safety

The `OktaTokenProvider` is thread-safe and can be shared across multiple threads. The internal cache handles concurrent access automatically.

## License

[Add your license information here]

## Contributing

[Add contribution guidelines here]

## Support

For issues related to:
- **This library**: [Add your support contact/link]
- **Okta**: Visit [Okta Developer Documentation](https://developer.okta.com/)