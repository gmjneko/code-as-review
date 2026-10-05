package org.koaks.codereview.user.domain;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

@Getter
@Setter
@Builder
@TableName("sys_user")
public class SystemUser extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private String username;
    private String email;
    private String passwordHash;
    private String status;
}
