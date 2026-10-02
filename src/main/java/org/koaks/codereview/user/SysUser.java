package org.koaks.codereview.user;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private String username;
    private String email;
    private String passwordHash;
    private String status;
}
