package org.koaks.codereview.auth.dto;

import org.koaks.codereview.user.domain.SystemUser;

public record UserProfile(Long id, String username, String email) {

    public static UserProfile of(SystemUser u) {
        return new UserProfile(u.getId(), u.getUsername(), u.getEmail());
    }

}
