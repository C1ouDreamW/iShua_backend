package cn.heycloudream.ishua_backend.vo.turnstile;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Cloudflare Turnstile Siteverify API response.
 */
@Data
public class TurnstileSiteverifyResponse {

    private boolean success;

    @JsonProperty("challenge_ts")
    private String challengeTs;

    private String hostname;

    @JsonProperty("error-codes")
    private List<String> errorCodes;

    private String action;

    private String cdata;
}
