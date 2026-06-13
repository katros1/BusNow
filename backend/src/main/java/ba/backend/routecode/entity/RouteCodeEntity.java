package ba.backend.routecode.entity;

import ba.backend.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "busnow_route_code")
public class RouteCodeEntity extends BaseEntity {

    @Column(name = "rc_code", nullable = false, unique = true)
    private String code;

    protected RouteCodeEntity() {
    }

    public RouteCodeEntity(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
