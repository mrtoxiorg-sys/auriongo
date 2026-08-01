package dev.toxi.aurionGo.feature.player;

public record HideSettings(
    boolean hideJoinLeaveMessages,
    boolean hideAfkMessages,
    boolean spyEnabled,
    boolean hideNametag
) {
    public static final HideSettings DEFAULT = new HideSettings(
        false,
        false,
        false,
        false
    );

    public HideSettings withHideJoinLeaveMessages(boolean value) {
        return new HideSettings(
            value,
            this.hideAfkMessages,
            this.spyEnabled,
            this.hideNametag
        );
    }

    public HideSettings withHideAfkMessages(boolean value) {
        return new HideSettings(
            this.hideJoinLeaveMessages,
            value,
            this.spyEnabled,
            this.hideNametag
        );
    }

    public HideSettings withSpyEnabled(boolean value) {
        return new HideSettings(
            this.hideJoinLeaveMessages,
            this.hideAfkMessages,
            value,
            this.hideNametag
        );
    }

    public HideSettings withHideNametag(boolean value) {
        return new HideSettings(
            this.hideJoinLeaveMessages,
            this.hideAfkMessages,
            this.spyEnabled,
            value
        );
    }
}
