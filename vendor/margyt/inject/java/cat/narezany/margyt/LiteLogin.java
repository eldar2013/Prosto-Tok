package cat.narezany.margyt;

import android.app.Activity;

/**
 * Leaves TikTok's own sign-in flow intact.
 *
 * Google OAuth is tied to the original TikTok application identity and its
 * server-side configuration. Rewriting the provider to a made-up value
 * guarantees failure and also prevents TikTok from attempting its normal
 * Google sign-in flow. The mod must not intercept, forge, or relay OAuth
 * credentials; it simply lets the original activity handle the request.
 */
public final class LiteLogin {

    private LiteLogin() {}

    /** Kept as the native activity hook; deliberately does not alter its intent. */
    static void created(Activity screen) {
        // Do not mutate provider_id, action_type, or any OAuth intent extras.
    }
}
