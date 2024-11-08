package normalmanv2.normalDiscGolf.impl.invite;

import java.time.Instant;
import java.util.UUID;

public record Invite(UUID invitee, UUID inviter, Instant expiryTime) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiryTime);
    }

}
