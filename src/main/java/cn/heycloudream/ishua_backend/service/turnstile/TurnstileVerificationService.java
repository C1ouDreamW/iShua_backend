package cn.heycloudream.ishua_backend.service.turnstile;

/**
 * Verifies Cloudflare Turnstile tokens via the Siteverify API.
 */
public interface TurnstileVerificationService {

    /**
     * Validates a Turnstile token for the register email-code action.
     *
     * @param token    client-side Turnstile response token
     * @param remoteIp visitor IP address (optional but recommended)
     */
    void verifyRegisterEmailCode(String token, String remoteIp);
}
