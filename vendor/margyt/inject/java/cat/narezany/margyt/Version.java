package cat.narezany.margyt;

/**
 * Written by the build. Do not edit.
 *
 * MOD is this repository's VERSION file; TIKTOK is what the apk this
 * was built from calls itself. The first is compared against the
 * repository to know whether there is an update; the second is there
 * so a person reporting something can say which TikTok it happened on.
 *
 * TEST marks a build made for the people who paid for the work: it
 * carries their account id faintly on screen and opens the mod's
 * settings only for them. A release build has it false and none of
 * that code ever runs.
 */
final class Version {

    private Version() {}

    static final String MOD = "2.3.0";
    /** What the update is called, from VERSION-NAME; empty when it has no name. */
    static final String NAME = "Epic Style Update";
    static final String TIKTOK = "47.2.41";
    static final boolean TEST = false;
    /** A profiling build (MARGYT_PROFILE=1): the mod's probes write to the log. */
    static final boolean PROFILE = false;
    /** Built into TikTok Lite: only what does not need TikTok's own classes runs. */
    static final boolean LITE = false;
}
